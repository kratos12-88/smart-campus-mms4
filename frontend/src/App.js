import React, { useEffect, useState } from "react";
import { api } from "./api";

const SERVICES = [
  ["ICT", "ICT & Network", "Wi-Fi, portals, computers and digital services."],
  ["FACILITIES", "Facilities", "Classrooms, furniture and maintenance."],
  ["ELECTRICITY", "Electricity", "Power, lighting and electrical faults."],
  ["WATER", "Water", "Water supply, plumbing and sanitation."],
  ["SECURITY", "Security", "Safety concerns and access issues."],
  ["HOSTEL", "Hostel", "Accommodation and residence facilities."]
];

const routeNow = () => window.location.hash.replace(/^#/, "") || "/";
const go = path => { window.location.hash = path; };
const Link = ({to, className="", children}) => <a className={className} href={`#${to}`}>{children}</a>;

function useRoute() {
  const [route, setRoute] = useState(routeNow());
  useEffect(() => {
    const onHash = () => setRoute(routeNow());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);
  return route;
}

function Page({eyebrow,title,children}) {
  return <section className="page"><div className="shell">
    <div className="page-head"><span className="eyebrow">{eyebrow}</span><h1>{title}</h1></div>{children}
  </div></section>;
}
function Field({label,children}) { return <label className="field"><span>{label}</span>{children}</label>; }
function Metric({label,value}) { return <div className="metric-card"><span>{label}</span><strong>{value}</strong></div>; }

function Shell({user,setUser,route,children}) {
  const logout = async () => {
    try { await api.logout(); } catch {}
    localStorage.removeItem("sc_token");
    localStorage.removeItem("sc_user");
    setUser(null); go("/");
  };
  const active = p => route===p ? "active" : "";
  return <>
    <header className="topbar"><div className="shell nav-shell">
      <Link className="brand" to="/"><span className="brand-mark">SC</span><span><b>SMART CAMPUS</b><small>Service & complaint management</small></span></Link>
      <nav>
        <Link className={active("/services")} to="/services">Services</Link>
        <Link className={active("/report")} to="/report">Report</Link>
        <Link className={active("/track")} to="/track">Track</Link>
        {user?.role==="STUDENT" && <Link className={active("/portal")} to="/portal">Portal</Link>}
        {["STAFF","ADMIN"].includes(user?.role) && <Link className={active("/staff")} to="/staff">Operations</Link>}
        {user?.role==="ADMIN" && <Link className={active("/admin")} to="/admin">Admin</Link>}
      </nav>
      {user ? <button className="btn ghost" onClick={logout}>Sign out</button> : <Link className="btn primary" to="/login">Sign in</Link>}
    </div></header>
    <main>{children}</main>
    <footer><div className="shell footer-grid">
      <div><b>SMART CAMPUS</b><p>Students report. Teams respond. Management sees what is working.</p></div>
      <div><b>Product</b><Link to="/services">Services</Link><Link to="/report">Report issue</Link></div>
      <div><b>Access</b><Link to="/login">Sign in</Link><Link to="/track">Track complaint</Link></div>
    </div></footer>
  </>;
}

function Home({summary}) {
  return <>
    <section className="hero"><div className="shell hero-grid">
      <div><span className="eyebrow">Smart campus service platform</span><h1>Report it. Track it. <em>Get it fixed.</em></h1>
      <p>A campus complaint system for students, staff and administrators.</p>
      <div className="actions"><Link className="btn primary" to="/report">Report an issue</Link><Link className="btn ghost" to="/track">Track complaint</Link></div></div>
      <aside className="dashboard-card"><div className="stats">
        <div><strong>{summary.total||0}</strong><span>Total</span></div><div><strong>{summary.open||0}</strong><span>Open</span></div>
        <div><strong>{summary.resolved||0}</strong><span>Resolved</span></div><div><strong>{summary.overdue||0}</strong><span>Overdue</span></div>
      </div></aside>
    </div></section>
    <section className="section white"><div className="shell"><span className="eyebrow">Campus services</span><h2>One place for campus support.</h2>
      <div className="service-grid">{SERVICES.map(([c,t,x])=><article className="card" key={c}><div className="code">{c.slice(0,3)}</div><h3>{t}</h3><p>{x}</p><Link to="/report">Report issue →</Link></article>)}</div>
    </div></section>
  </>;
}

function Login({setUser}) {
  const [registerMode,setRegisterMode]=useState(false);
  const [form,setForm]=useState({name:"",email:"",password:"",confirmPassword:"",schoolName:"",campusName:""});
  const [error,setError]=useState("");
  const [fieldErrors,setFieldErrors]=useState({});
  const [busy,setBusy]=useState(false);
  const [showPassword,setShowPassword]=useState(false);

  const update=(key,value)=>{
    setForm({...form,[key]:value});
    setFieldErrors({...fieldErrors,[key]:""});
    setError("");
  };

  const validate=()=>{
    const next={};
    const email=form.email.trim();
    if(!email) next.email="Email is required.";
    else if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) next.email="Enter a valid email address.";

    if(!form.password) next.password="Password is required.";
    if(registerMode){
      const name=form.name.trim().replace(/\s+/g," ");
      if(name.length<2) next.name="Enter your full name.";
      else if(name.length>80) next.name="Full name must be 80 characters or fewer.";

      if(form.schoolName.trim().length<2) next.schoolName="School is required.";
      if(form.campusName.trim().length<2) next.campusName="Campus is required.";

      if(form.password.length<8) next.password="Password must be at least 8 characters.";
      else if(form.password.length>128) next.password="Password must be 128 characters or fewer.";
      else if(!/[A-Z]/.test(form.password)||!/[a-z]/.test(form.password)||!/\d/.test(form.password))
        next.password="Use uppercase, lowercase, and at least one number.";
      if(!form.confirmPassword) next.confirmPassword="Please confirm your password.";
      else if(form.confirmPassword!==form.password) next.confirmPassword="Passwords do not match.";
    }

    setFieldErrors(next);
    return Object.keys(next).length===0;
  };

  const submit=async e=>{
    e.preventDefault();
    if(busy||!validate()) return;
    setBusy(true); setError("");
    try{
      const payload={
        ...form,
        name:form.name.trim().replace(/\s+/g," "),
        email:form.email.trim().toLowerCase(),
        schoolName:form.schoolName.trim().replace(/\s+/g," "),
        campusName:form.campusName.trim().replace(/\s+/g," ")
      };
      const data=registerMode?await api.register(payload):await api.login({email:payload.email,password:form.password});
      localStorage.setItem("sc_token",data.token);
      const profile={...data}; delete profile.token;
      localStorage.setItem("sc_user",JSON.stringify(profile));
      setUser(profile);
      go(profile.role==="STUDENT"?"/portal":profile.role==="ADMIN"?"/admin":"/staff");
    }catch(e){
      setError(e.message||"Unable to sign in. Please try again.");
    }finally{
      setBusy(false);
    }
  };

  const switchMode=()=>{
    setRegisterMode(!registerMode);
    setError("");
    setFieldErrors({});
    setShowPassword(false);
    setForm({name:"",email:form.email.trim(),password:"",confirmPassword:"",schoolName:"",campusName:""});
  };

  return <Page eyebrow="Account access" title={registerMode?"Create student account":"Sign in"}>
    <form className="form-card auth-card" onSubmit={submit} noValidate>
      {registerMode && <>
        <Field label="Full name">
          <input autoComplete="name" maxLength="80" aria-invalid={!!fieldErrors.name} value={form.name} onChange={e=>update("name",e.target.value)}/>
          {fieldErrors.name&&<small className="field-error">{fieldErrors.name}</small>}
        </Field>
        <Field label="School">
          <input autoComplete="organization" maxLength="120" aria-invalid={!!fieldErrors.schoolName} value={form.schoolName} onChange={e=>update("schoolName",e.target.value)}/>
          {fieldErrors.schoolName&&<small className="field-error">{fieldErrors.schoolName}</small>}
        </Field>
        <Field label="Campus">
          <input maxLength="120" aria-invalid={!!fieldErrors.campusName} value={form.campusName} onChange={e=>update("campusName",e.target.value)}/>
          {fieldErrors.campusName&&<small className="field-error">{fieldErrors.campusName}</small>}
        </Field>
      </>}
      <Field label="Email">
        <input type="email" inputMode="email" autoCapitalize="none" autoComplete="email" maxLength="254" aria-invalid={!!fieldErrors.email} value={form.email} onChange={e=>update("email",e.target.value)}/>
        {fieldErrors.email&&<small className="field-error">{fieldErrors.email}</small>}
      </Field>
      <Field label="Password">
        <div className="password-row">
          <input type={showPassword?"text":"password"} autoComplete={registerMode?"new-password":"current-password"} maxLength="128" aria-invalid={!!fieldErrors.password} value={form.password} onChange={e=>update("password",e.target.value)}/>
          <button type="button" className="password-toggle" onClick={()=>setShowPassword(!showPassword)}>{showPassword?"Hide":"Show"}</button>
        </div>
        {registerMode&&<small className="field-help">8–128 characters with uppercase, lowercase, and a number.</small>}
        {fieldErrors.password&&<small className="field-error">{fieldErrors.password}</small>}
      </Field>
      {registerMode&&<Field label="Confirm password">
        <input type={showPassword?"text":"password"} autoComplete="new-password" maxLength="128" aria-invalid={!!fieldErrors.confirmPassword} value={form.confirmPassword} onChange={e=>update("confirmPassword",e.target.value)}/>
        {fieldErrors.confirmPassword&&<small className="field-error">{fieldErrors.confirmPassword}</small>}
      </Field>}
      {error&&<div className="error" role="alert">{error}</div>}
      <button className="btn primary wide" disabled={busy}>{busy?"Please wait…":registerMode?"Create account":"Sign in"}</button>
      <button type="button" className="text-btn" onClick={switchMode} disabled={busy}>{registerMode?"Already registered? Sign in":"New student? Create an account"}</button>
    </form>
  </Page>;
}

function Report({user}) {
  const [form,setForm]=useState({title:"",description:"",location:"Main Library",category:"ICT",priority:"MEDIUM"});
  const [point,setPoint]=useState(null);
  const [error,setError]=useState("");
  const [fieldErrors,setFieldErrors]=useState({});
  const [busy,setBusy]=useState(false);

  if(!user||user.role!=="STUDENT") return <Page eyebrow="Student reporting" title="Sign in before reporting"><Link className="btn primary" to="/login">Sign in</Link></Page>;

  const update=(key,value)=>{
    setForm({...form,[key]:value});
    setFieldErrors({...fieldErrors,[key]:""});
    setError("");
  };

  const validate=()=>{
    const next={};
    const title=form.title.trim().replace(/\s+/g," ");
    const description=form.description.trim().replace(/\s+/g," ");

    if(title.length<5) next.title="Issue title must be at least 5 characters.";
    else if(title.length>120) next.title="Issue title must be 120 characters or fewer.";

    if(description.length<10) next.description="Description must be at least 10 characters.";
    else if(description.length>2000) next.description="Description must be 2000 characters or fewer.";

    if(!SERVICES.some(s=>s[0]===form.category)) next.category="Select a valid category.";
    if(!["LOW","MEDIUM","HIGH","URGENT"].includes(form.priority)) next.priority="Select a valid priority.";
    if(!form.location) next.location="Select a campus location.";
    if(!point) next.map="Tag the issue location on the campus map.";

    setFieldErrors(next);
    return Object.keys(next).length===0;
  };

  const submit=async e=>{
    e.preventDefault();
    if(busy||!validate()) return;
    setBusy(true); setError("");
    try{
      const payload={
        ...form,
        title:form.title.trim().replace(/\s+/g," "),
        description:form.description.trim().replace(/\s+/g," "),
        ...point,
        mapLabel:form.location
      };
      const complaint=await api.createComplaint(payload);
      go(`/complaint/${complaint.id}`);
    }catch(e){
      setError(e.message||"Unable to submit complaint. Please review the form and try again.");
    }finally{
      setBusy(false);
    }
  };

  const mapClick=e=>{
    const r=e.currentTarget.getBoundingClientRect();
    setPoint({
      mapX:+(((e.clientX-r.left)/r.width)*100).toFixed(2),
      mapY:+(((e.clientY-r.top)/r.height)*100).toFixed(2)
    });
    setFieldErrors({...fieldErrors,map:""});
    setError("");
  };

  return <Page eyebrow="New complaint" title="Report a campus issue"><div className="report-layout">
    <aside className="side-card"><h3>{user.schoolName}</h3><p>{user.campusName}</p><p>Choose a service, describe the problem and tag its location.</p></aside>
    <form className="form-card" onSubmit={submit} noValidate>
      <div className="form-grid">
        <Field label="Issue title">
          <input maxLength="120" aria-invalid={!!fieldErrors.title} value={form.title} onChange={e=>update("title",e.target.value)}/>
          <small className="field-help">{form.title.length}/120 characters</small>
          {fieldErrors.title&&<small className="field-error">{fieldErrors.title}</small>}
        </Field>
        <Field label="Priority">
          <select aria-invalid={!!fieldErrors.priority} value={form.priority} onChange={e=>update("priority",e.target.value)}>
            <option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>URGENT</option>
          </select>
          {fieldErrors.priority&&<small className="field-error">{fieldErrors.priority}</small>}
        </Field>
      </div>
      <div className="form-grid">
        <Field label="Category">
          <select aria-invalid={!!fieldErrors.category} value={form.category} onChange={e=>update("category",e.target.value)}>
            {SERVICES.map(s=><option key={s[0]} value={s[0]}>{s[1]}</option>)}
          </select>
          {fieldErrors.category&&<small className="field-error">{fieldErrors.category}</small>}
        </Field>
        <Field label="Location">
          <select aria-invalid={!!fieldErrors.location} value={form.location} onChange={e=>update("location",e.target.value)}>
            <option>Main Library</option><option>Lecture Hall A</option><option>Admin Block</option><option>Hostel</option><option>Main Gate</option>
          </select>
          {fieldErrors.location&&<small className="field-error">{fieldErrors.location}</small>}
        </Field>
      </div>
      <Field label="Description">
        <textarea maxLength="2000" aria-invalid={!!fieldErrors.description} value={form.description} onChange={e=>update("description",e.target.value)}/>
        <small className="field-help">{form.description.length}/2000 characters</small>
        {fieldErrors.description&&<small className="field-error">{fieldErrors.description}</small>}
      </Field>
      <div className={`campus-map ${fieldErrors.map?"map-invalid":""}`} onClick={mapClick} role="button" tabIndex="0" aria-label="Campus location map">
        <span className="building library">Library</span><span className="building halls">Lecture halls</span><span className="building hostel">Hostels</span><span className="building admin">Admin</span>
        {point&&<span className="pin" style={{left:`${point.mapX}%`,top:`${point.mapY}%`}}/>}
      </div>
      {point?<small className="field-help">Location tagged at {point.mapX}%, {point.mapY}%.</small>:<small className="field-help">Click the map to tag the exact issue location.</small>}
      {fieldErrors.map&&<small className="field-error">{fieldErrors.map}</small>}
      {error&&<div className="error" role="alert">{error}</div>}
      <button className="btn primary wide" disabled={busy}>{busy?"Submitting…":"Submit complaint"}</button>
    </form>
  </div></Page>;
}

function Track(){
  const[id,setId]=useState("");
  const[error,setError]=useState("");
  const submit=e=>{
    e.preventDefault();
    const value=id.trim();
    if(!value) return setError("Complaint reference is required.");
    if(!/^\d+$/.test(value)) return setError("Complaint reference must contain numbers only.");
    if(Number(value)<=0) return setError("Complaint reference must be greater than zero.");
    if(value.length>12) return setError("Complaint reference is too long.");
    setError("");
    go(`/complaint/${value}`);
  };
  return <Page eyebrow="Complaint tracking" title="Track a complaint">
    <form className="form-card track-form" onSubmit={submit} noValidate>
      <Field label="Complaint reference">
        <input inputMode="numeric" pattern="[0-9]*" maxLength="12" aria-invalid={!!error} value={id} onChange={e=>{setId(e.target.value);setError("")}} placeholder="e.g. 1001"/>
        {error&&<small className="field-error">{error}</small>}
      </Field>
      <button className="btn primary">Track complaint</button>
    </form>
  </Page>
}

function Portal({user}){const[items,setItems]=useState([]);useEffect(()=>{if(user?.role==="STUDENT")api.mine().then(setItems).catch(()=>{})},[user]);if(!user||user.role!=="STUDENT")return <Page eyebrow="Student portal" title="Sign in required"><Link className="btn primary" to="/login">Sign in</Link></Page>;return <Page eyebrow="Student portal" title={`Welcome, ${user.name.split(" ")[0]}`}><div className="portal-grid"><section><div className="quick-grid"><Link className="quick" to="/report"><b>Report issue</b><span>Create a complaint</span></Link><Link className="quick" to="/track"><b>Track reference</b><span>Open by number</span></Link></div><h2>My complaints</h2><ComplaintList items={items}/></section><aside className="side-card"><h3>{user.schoolName}</h3><p>{user.campusName}</p></aside></div></Page>}

function Staff({user}){
  const[items,setItems]=useState([]);
  const[error,setError]=useState("");
  const[busyId,setBusyId]=useState(null);

  const load=()=>api.staffComplaints().then(data=>{setItems(data);setError("")}).catch(e=>setError(e.message||"Unable to load complaints."));
  useEffect(()=>{if(["STAFF","ADMIN"].includes(user?.role))load()},[user]);

  if(!["STAFF","ADMIN"].includes(user?.role))return <Page eyebrow="Operations" title="Staff access required"><Link className="btn primary" to="/login">Sign in</Link></Page>;

  const transitions=status=>{
    if(status==="SUBMITTED") return ["SUBMITTED","ASSIGNED","REJECTED"];
    if(status==="ASSIGNED") return ["ASSIGNED","IN_PROGRESS","REJECTED"];
    if(status==="IN_PROGRESS") return ["IN_PROGRESS","RESOLVED","REJECTED"];
    if(status==="RESOLVED") return ["RESOLVED","CLOSED","IN_PROGRESS"];
    return [status];
  };

  const changeStatus=async(c,next)=>{
    if(next===c.status) return;
    setBusyId(c.id); setError("");
    try{
      await api.updateStatus(c.id,next);
      await load();
    }catch(e){
      setError(`Complaint #${c.id}: ${e.message}`);
      await load();
    }finally{
      setBusyId(null);
    }
  };

  const assign=async(c,value)=>{
    const assignedTo=value.trim().replace(/\s+/g," ");
    if(assignedTo.length<2) return setError(`Complaint #${c.id}: assignee must be at least 2 characters.`);
    if(assignedTo.length>80) return setError(`Complaint #${c.id}: assignee must be 80 characters or fewer.`);
    if(assignedTo===(c.assignedTo||"").trim()) return;

    setBusyId(c.id); setError("");
    try{
      await api.assign(c.id,assignedTo);
      await load();
    }catch(e){
      setError(`Complaint #${c.id}: ${e.message}`);
      await load();
    }finally{
      setBusyId(null);
    }
  };

  return <Page eyebrow="Operations console" title="Staff complaint management">
    {error&&<div className="error" role="alert">{error}</div>}
    <div className="table-wrap"><table><thead><tr><th>Ref</th><th>Issue</th><th>Location</th><th>Priority</th><th>Status</th><th>Assigned</th></tr></thead>
      <tbody>{items.map(c=><tr key={c.id}>
        <td><Link to={`/complaint/${c.id}`}>#{c.id}</Link></td>
        <td>{c.title}</td><td>{c.location}</td><td>{c.priority}</td>
        <td><select value={c.status} disabled={busyId===c.id||["CLOSED","REJECTED"].includes(c.status)} onChange={e=>changeStatus(c,e.target.value)}>
          {transitions(c.status).map(s=><option key={s}>{s}</option>)}
        </select></td>
        <td><input key={`${c.id}-${c.assignedTo||""}`} maxLength="80" disabled={busyId===c.id||["RESOLVED","CLOSED","REJECTED"].includes(c.status)} defaultValue={c.assignedTo||""} placeholder="Staff or team" onBlur={e=>assign(c,e.target.value)}/></td>
      </tr>)}</tbody>
    </table></div>
  </Page>
}

function Admin({user}){const[data,setData]=useState({});useEffect(()=>{if(user?.role==="ADMIN")api.analytics().then(setData).catch(()=>{})},[user]);if(user?.role!=="ADMIN")return <Page eyebrow="Admin" title="Administrator access required"><Link className="btn primary" to="/login">Sign in</Link></Page>;return <Page eyebrow="Administration" title="Service analytics"><div className="analytics-grid"><Metric label="Total complaints" value={data.total||0}/><Metric label="Average resolution" value={`${data.averageResolutionHours||0}h`}/><Metric label="Student rating" value={`${data.averageRating||0}/5`}/></div></Page>}

function ComplaintDetail({id}){const[item,setItem]=useState(null),[error,setError]=useState("");useEffect(()=>{api.one(id).then(setItem).catch(e=>setError(e.message))},[id]);if(error)return <Page eyebrow="Complaint" title="Complaint not found"><p>{error}</p></Page>;if(!item)return <Page eyebrow="Complaint" title="Loading complaint..."/>;return <Page eyebrow={`Complaint #${item.id}`} title={item.title}><div className="detail-card"><span className={`status ${item.status}`}>{item.status.replaceAll("_"," ")}</span><p>{item.description}</p><div className="detail-grid"><Metric label="Department" value={item.department}/><Metric label="Priority" value={item.priority}/><Metric label="Assigned to" value={item.assignedTo||"Unassigned"}/></div></div></Page>}
function Services(){return <Page eyebrow="Service directory" title="Campus support services"><div className="service-grid">{SERVICES.map(([c,t,x])=><article className="card" key={c}><div className="code">{c.slice(0,3)}</div><h3>{t}</h3><p>{x}</p><Link to="/report">Report issue →</Link></article>)}</div></Page>}
function ComplaintList({items}){return <div className="list">{items.length?items.map(c=><Link className="case-row" to={`/complaint/${c.id}`} key={c.id}><span>#{c.id}</span><b>{c.title}</b><small>{c.status.replaceAll("_"," ")}</small></Link>):<div className="empty">No complaints yet.</div>}</div>}

export default function App(){
  const route=useRoute();
  const[user,setUser]=useState(()=>JSON.parse(localStorage.getItem("sc_user")||"null"));
  const[summary,setSummary]=useState({});
  useEffect(()=>{
    api.summary().then(setSummary).catch(()=>{});
    if(localStorage.getItem("sc_token")){
      api.me().then(profile=>{
        setUser(profile);
        localStorage.setItem("sc_user",JSON.stringify(profile));
      }).catch(()=>{
        localStorage.removeItem("sc_token");
        localStorage.removeItem("sc_user");
        setUser(null);
      });
    }
  },[]);
  let content;
  if(route==="/")content=<Home summary={summary}/>;
  else if(route==="/services")content=<Services/>;
  else if(route==="/login")content=<Login setUser={setUser}/>;
  else if(route==="/report")content=<Report user={user}/>;
  else if(route==="/track")content=<Track/>;
  else if(route==="/portal")content=<Portal user={user}/>;
  else if(route==="/staff")content=<Staff user={user}/>;
  else if(route==="/admin")content=<Admin user={user}/>;
  else if(route.startsWith("/complaint/"))content=<ComplaintDetail id={route.split("/").pop()}/>;
  else content=<Page eyebrow="404" title="Page not found"><Link className="btn primary" to="/">Home</Link></Page>;
  return <Shell user={user} setUser={setUser} route={route}>{content}</Shell>;
}
