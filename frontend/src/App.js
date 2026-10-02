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
  const [form,setForm]=useState({name:"",email:"",password:"",schoolName:"",campusName:""});
  const [error,setError]=useState("");
  const submit=async e=>{
    e.preventDefault(); setError("");
    try{
      const data=registerMode?await api.register(form):await api.login(form);
      localStorage.setItem("sc_token",data.token);
      const profile={...data}; delete profile.token;
      localStorage.setItem("sc_user",JSON.stringify(profile)); setUser(profile);
      go(profile.role==="STUDENT"?"/portal":profile.role==="ADMIN"?"/admin":"/staff");
    }catch(e){setError(e.message)}
  };
  return <Page eyebrow="Account access" title={registerMode?"Create student account":"Sign in"}>
    <form className="form-card auth-card" onSubmit={submit}>
      {registerMode && <>
        <Field label="Full name"><input required value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></Field>
        <Field label="School"><input required value={form.schoolName} onChange={e=>setForm({...form,schoolName:e.target.value})}/></Field>
        <Field label="Campus"><input required value={form.campusName} onChange={e=>setForm({...form,campusName:e.target.value})}/></Field>
      </>}
      <Field label="Email"><input type="email" required value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></Field>
      <Field label="Password"><input type="password" minLength="8" required value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/></Field>
      {error&&<div className="error">{error}</div>}
      <button className="btn primary wide">{registerMode?"Create account":"Sign in"}</button>
      <button type="button" className="text-btn" onClick={()=>setRegisterMode(!registerMode)}>{registerMode?"Already registered? Sign in":"New student? Create an account"}</button>
    </form>
  </Page>;
}

function Report({user}) {
  const [form,setForm]=useState({title:"",description:"",location:"Main Library",category:"ICT",priority:"MEDIUM"});
  const [point,setPoint]=useState(null),[error,setError]=useState("");
  if(!user||user.role!=="STUDENT") return <Page eyebrow="Student reporting" title="Sign in before reporting"><Link className="btn primary" to="/login">Sign in</Link></Page>;
  const submit=async e=>{e.preventDefault();if(!point)return setError("Tag the issue location on the campus map.");try{const c=await api.createComplaint({...form,...point,mapLabel:form.location});go(`/complaint/${c.id}`)}catch(e){setError(e.message)}};
  const mapClick=e=>{const r=e.currentTarget.getBoundingClientRect();setPoint({mapX:+(((e.clientX-r.left)/r.width)*100).toFixed(2),mapY:+(((e.clientY-r.top)/r.height)*100).toFixed(2)})};
  return <Page eyebrow="New complaint" title="Report a campus issue"><div className="report-layout">
    <aside className="side-card"><h3>{user.schoolName}</h3><p>{user.campusName}</p><p>Choose a service, describe the problem and tag its location.</p></aside>
    <form className="form-card" onSubmit={submit}>
      <div className="form-grid">
        <Field label="Issue title"><input required value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/></Field>
        <Field label="Priority"><select value={form.priority} onChange={e=>setForm({...form,priority:e.target.value})}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>URGENT</option></select></Field>
      </div>
      <div className="form-grid">
        <Field label="Category"><select value={form.category} onChange={e=>setForm({...form,category:e.target.value})}>{SERVICES.map(s=><option key={s[0]} value={s[0]}>{s[1]}</option>)}</select></Field>
        <Field label="Location"><select value={form.location} onChange={e=>setForm({...form,location:e.target.value})}><option>Main Library</option><option>Lecture Hall A</option><option>Admin Block</option><option>Hostel</option><option>Main Gate</option></select></Field>
      </div>
      <Field label="Description"><textarea required value={form.description} onChange={e=>setForm({...form,description:e.target.value})}/></Field>
      <div className="campus-map" onClick={mapClick}><span className="building library">Library</span><span className="building halls">Lecture halls</span><span className="building hostel">Hostels</span><span className="building admin">Admin</span>{point&&<span className="pin" style={{left:`${point.mapX}%`,top:`${point.mapY}%`}}/>}</div>
      {error&&<div className="error">{error}</div>}<button className="btn primary wide">Submit complaint</button>
    </form>
  </div></Page>;
}

function Track(){const[id,setId]=useState("");return <Page eyebrow="Complaint tracking" title="Track a complaint"><form className="form-card track-form" onSubmit={e=>{e.preventDefault();if(id.trim())go(`/complaint/${id.trim()}`)}}><Field label="Complaint reference"><input value={id} onChange={e=>setId(e.target.value)} placeholder="e.g. 1001"/></Field><button className="btn primary">Track complaint</button></form></Page>}

function Portal({user}){const[items,setItems]=useState([]);useEffect(()=>{if(user?.role==="STUDENT")api.mine().then(setItems).catch(()=>{})},[user]);if(!user||user.role!=="STUDENT")return <Page eyebrow="Student portal" title="Sign in required"><Link className="btn primary" to="/login">Sign in</Link></Page>;return <Page eyebrow="Student portal" title={`Welcome, ${user.name.split(" ")[0]}`}><div className="portal-grid"><section><div className="quick-grid"><Link className="quick" to="/report"><b>Report issue</b><span>Create a complaint</span></Link><Link className="quick" to="/track"><b>Track reference</b><span>Open by number</span></Link></div><h2>My complaints</h2><ComplaintList items={items}/></section><aside className="side-card"><h3>{user.schoolName}</h3><p>{user.campusName}</p></aside></div></Page>}

function Staff({user}){const[items,setItems]=useState([]);const load=()=>api.staffComplaints().then(setItems).catch(()=>{});useEffect(()=>{if(["STAFF","ADMIN"].includes(user?.role))load()},[user]);if(!["STAFF","ADMIN"].includes(user?.role))return <Page eyebrow="Operations" title="Staff access required"><Link className="btn primary" to="/login">Sign in</Link></Page>;return <Page eyebrow="Operations console" title="Staff complaint management"><div className="table-wrap"><table><thead><tr><th>Ref</th><th>Issue</th><th>Location</th><th>Priority</th><th>Status</th><th>Assigned</th></tr></thead><tbody>{items.map(c=><tr key={c.id}><td><Link to={`/complaint/${c.id}`}>#{c.id}</Link></td><td>{c.title}</td><td>{c.location}</td><td>{c.priority}</td><td><select value={c.status} onChange={async e=>{await api.updateStatus(c.id,e.target.value);load()}}><option>SUBMITTED</option><option>ASSIGNED</option><option>IN_PROGRESS</option><option>RESOLVED</option><option>CLOSED</option></select></td><td><input defaultValue={c.assignedTo||user.name} onBlur={async e=>{await api.assign(c.id,e.target.value);load()}}/></td></tr>)}</tbody></table></div></Page>}

function Admin({user}){const[data,setData]=useState({});useEffect(()=>{if(user?.role==="ADMIN")api.analytics().then(setData).catch(()=>{})},[user]);if(user?.role!=="ADMIN")return <Page eyebrow="Admin" title="Administrator access required"><Link className="btn primary" to="/login">Sign in</Link></Page>;return <Page eyebrow="Administration" title="Service analytics"><div className="analytics-grid"><Metric label="Total complaints" value={data.total||0}/><Metric label="Average resolution" value={`${data.averageResolutionHours||0}h`}/><Metric label="Student rating" value={`${data.averageRating||0}/5`}/></div></Page>}

function ComplaintDetail({id}){const[item,setItem]=useState(null),[error,setError]=useState("");useEffect(()=>{api.one(id).then(setItem).catch(e=>setError(e.message))},[id]);if(error)return <Page eyebrow="Complaint" title="Complaint not found"><p>{error}</p></Page>;if(!item)return <Page eyebrow="Complaint" title="Loading complaint..."/>;return <Page eyebrow={`Complaint #${item.id}`} title={item.title}><div className="detail-card"><span className={`status ${item.status}`}>{item.status.replaceAll("_"," ")}</span><p>{item.description}</p><div className="detail-grid"><Metric label="Department" value={item.department}/><Metric label="Priority" value={item.priority}/><Metric label="Assigned to" value={item.assignedTo||"Unassigned"}/></div></div></Page>}
function Services(){return <Page eyebrow="Service directory" title="Campus support services"><div className="service-grid">{SERVICES.map(([c,t,x])=><article className="card" key={c}><div className="code">{c.slice(0,3)}</div><h3>{t}</h3><p>{x}</p><Link to="/report">Report issue →</Link></article>)}</div></Page>}
function ComplaintList({items}){return <div className="list">{items.length?items.map(c=><Link className="case-row" to={`/complaint/${c.id}`} key={c.id}><span>#{c.id}</span><b>{c.title}</b><small>{c.status.replaceAll("_"," ")}</small></Link>):<div className="empty">No complaints yet.</div>}</div>}

export default function App(){
  const route=useRoute();
  const[user,setUser]=useState(()=>JSON.parse(localStorage.getItem("sc_user")||"null"));
  const[summary,setSummary]=useState({});
  useEffect(()=>{api.summary().then(setSummary).catch(()=>{});if(localStorage.getItem("sc_token"))api.me().then(setUser).catch(()=>{})},[]);
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
