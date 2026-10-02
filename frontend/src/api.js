const API_BASE = process.env.REACT_APP_API_URL || "http://localhost:8080/api";

async function request(path, options = {}) {
  const token = localStorage.getItem("sc_token");
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || "Request failed");
  return data;
}

export const api = {
  summary: () => request("/dashboard/summary"),
  recent: () => request("/complaints"),
  mine: () => request("/complaints/mine"),
  one: id => request(`/complaints/${id}`),
  login: body => request("/auth/login", { method: "POST", body: JSON.stringify(body) }),
  register: body => request("/auth/register", { method: "POST", body: JSON.stringify(body) }),
  me: () => request("/auth/me"),
  logout: () => request("/auth/logout", { method: "POST" }),
  createComplaint: body => request("/complaints", { method: "POST", body: JSON.stringify(body) }),
  staffComplaints: () => request("/staff/complaints"),
  updateStatus: (id,status) => request(`/complaints/${id}/status`, { method:"PUT", body:JSON.stringify({status}) }),
  assign: (id,assignedTo) => request(`/complaints/${id}/assignment`, { method:"PUT", body:JSON.stringify({assignedTo}) }),
  analytics: () => request("/dashboard/analytics")
};
