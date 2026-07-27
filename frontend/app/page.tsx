"use client";

import {
  AlertCircle, CheckCircle2, ChevronRight, FileText, FolderKanban,
  LogIn, LogOut, Menu, Plus, RefreshCw, Search, ShieldCheck, UploadCloud, X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { User, UserManager, WebStorageStateStore } from "oidc-client-ts";

type Tender = { id:string; code:string; name:string; contractingAuthority:string; priority:string; status:string };
type DocumentItem = { id:string; projectId:string; name:string; type:string; status:string; currentVersion:number; createdAt:string };
type Clause = { id:string; parentId?:string; number:string; title:string; sourceText:string; pageNumber:number; sortOrder:number };

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const oidc = typeof window === "undefined" ? null : new UserManager({
  authority: process.env.NEXT_PUBLIC_OIDC_ISSUER ?? "http://localhost:8081/realms/specai",
  client_id: process.env.NEXT_PUBLIC_OIDC_CLIENT_ID ?? "specai-portal",
  redirect_uri: `${window.location.origin}/`, post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: "code", scope: "openid profile email",
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});

async function api<T>(path:string, user:User, init?:RequestInit):Promise<T> {
  const response = await fetch(`${API}${path}`, { ...init, headers: {
    Authorization:`Bearer ${user.access_token}`,
    ...(init?.body instanceof FormData ? {} : {"Content-Type":"application/json"}), ...init?.headers,
  }});
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail ?? `İstek başarısız (${response.status})`);
  }
  return response.status === 204 ? undefined as T : response.json();
}

export default function Platform() {
  const [user,setUser]=useState<User|null>(null), [projects,setProjects]=useState<Tender[]>([]);
  const [selected,setSelected]=useState<Tender|null>(null), [documents,setDocuments]=useState<DocumentItem[]>([]);
  const [activeDocument,setActiveDocument]=useState<DocumentItem|null>(null), [clauses,setClauses]=useState<Clause[]>([]);
  const [query,setQuery]=useState(""), [busy,setBusy]=useState(false), [message,setMessage]=useState("");
  const [newProject,setNewProject]=useState(false), [mobileNav,setMobileNav]=useState(false);

  const loadProjects=useCallback(async(identity:User)=>{
    const page=await api<{content:Tender[]}>("/api/v1/tenders?size=50&sort=createdAt,desc",identity);
    setProjects(page.content); setSelected(current=>current??page.content[0]??null);
  },[]);

  useEffect(()=>{ if(!oidc)return; void(async()=>{
    try {
      const params=new URLSearchParams(window.location.search);
      const identity=params.has("code")?await oidc.signinRedirectCallback():await oidc.getUser();
      if(params.has("code"))window.history.replaceState({},"","/");
      if(identity&&!identity.expired){setUser(identity);await loadProjects(identity);}
    } catch(error){setMessage(error instanceof Error?error.message:"Oturum açılamadı");}
  })(); },[loadProjects]);

  const loadDocuments=useCallback(async()=>{
    if(!user||!selected)return;
    const items=await api<DocumentItem[]>(`/api/v1/tenders/${selected.id}/documents`,user);
    setDocuments(items);
    if(activeDocument)setActiveDocument(items.find(item=>item.id===activeDocument.id)??null);
  },[activeDocument,selected,user]);

  useEffect(()=>{void loadDocuments();},[selected?.id,user?.access_token]);
  useEffect(()=>{if(!activeDocument||activeDocument.status!=="READY"||!user){setClauses([]);return;}
    void api<Clause[]>(`/api/v1/documents/${activeDocument.id}/clauses`,user).then(setClauses).catch(e=>setMessage(e.message));
  },[activeDocument,user]);

  const visibleProjects=useMemo(()=>projects.filter(p=>`${p.code} ${p.name} ${p.contractingAuthority}`.toLocaleLowerCase("tr").includes(query.toLocaleLowerCase("tr"))),[projects,query]);

  async function createProject(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!user)return;const form=new FormData(event.currentTarget);setBusy(true);
    try{const project=await api<Tender>("/api/v1/tenders",user,{method:"POST",body:JSON.stringify({
      name:form.get("name"),contractingAuthority:form.get("authority"),registrationNumber:form.get("registration")||null,
      deadline:form.get("deadline")||null,currency:"TRY",priority:form.get("priority"),description:form.get("description")||null,
    })});setProjects(current=>[project,...current]);setSelected(project);setNewProject(false);setMessage("Proje oluşturuldu.");}
    catch(error){setMessage(error instanceof Error?error.message:"Proje oluşturulamadı");}finally{setBusy(false);}
  }

  async function upload(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!user||!selected)return;const form=new FormData(event.currentTarget);
    const file=form.get("file");if(!(file instanceof File)||!file.size)return;
    const payload=new FormData();payload.set("type",String(form.get("type")));payload.set("file",file);setBusy(true);
    try{await api(`/api/v1/tenders/${selected.id}/documents`,user,{method:"POST",body:payload});event.currentTarget.reset();await loadDocuments();setMessage("Belge güvenli işleme kuyruğuna alındı.");}
    catch(error){setMessage(error instanceof Error?error.message:"Belge yüklenemedi");}finally{setBusy(false);}
  }

  async function openPreview(){
    if(!user||!activeDocument)return;
    try{
      const preview=await api<{url:string}>(`/api/v1/documents/${activeDocument.id}/preview`,user);
      window.open(preview.url,"_blank","noopener,noreferrer");
    }catch(error){setMessage(error instanceof Error?error.message:"Belge açılamadı");}
  }

  if(!user)return <main className="login-shell"><section className="login-card">
    <div className="brand-symbol">N</div><p className="eyebrow">NANObaseAI · SPECAI</p>
    <h1>Teknik şartnameyi<br/>karara dönüştürün.</h1>
    <p className="login-copy">Belgeleri güvenli ortamınızdan çıkarmadan inceleyin, maddeleri kaynaklarıyla yönetin.</p>
    <div className="security-note"><ShieldCheck/><span>On-premise · Tenant izoleli · Audit kayıtlı</span></div>
    <button className="primary large" onClick={()=>void oidc?.signinRedirect()}><LogIn/> Güvenli giriş</button>
    {message&&<p className="error"><AlertCircle/>{message}</p>}
  </section></main>;

  return <div className="app-shell">
    {mobileNav&&<button className="scrim" onClick={()=>setMobileNav(false)} aria-label="Menüyü kapat"/>}
    <aside className={mobileNav?"sidebar open":"sidebar"}>
      <div className="brand"><span className="brand-symbol small">N</span><div><b>NANObaseAI</b><small>Şartname İnceleme</small></div><button className="close-mobile" onClick={()=>setMobileNav(false)}><X/></button></div>
      <p className="nav-label">ÇALIŞMA ALANI</p><button className="nav active"><FolderKanban/><span>İhale projeleri</span><b>{projects.length}</b></button><button className="nav"><FileText/><span>Doküman merkezi</span></button>
      <div className="engine"><ShieldCheck/><div><b>Yerel analiz motoru</b><small>Güvenli bağlantı</small></div></div>
      <button className="profile" onClick={()=>void oidc?.signoutRedirect()}><span>MA</span><div><b>{user.profile.name??user.profile.email}</b><small>Oturumu kapat</small></div><LogOut/></button>
    </aside>
    <main className="main"><header><button className="mobile-menu" onClick={()=>setMobileNav(true)}><Menu/></button><div className="search"><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Projelerde ara…"/></div><button className="refresh" onClick={()=>loadProjects(user)}><RefreshCw/> Yenile</button></header>
      <div className="content"><div className="title-row"><div><p className="eyebrow">İHALE ÇALIŞMA ALANI</p><h1>Projeler ve dokümanlar</h1><p>Şartnameyi yükleyin; güvenlik taraması ve madde çıkarımı otomatik başlasın.</p></div><button className="primary" onClick={()=>setNewProject(true)}><Plus/> Yeni proje</button></div>
        <section className="workspace-grid"><article className="panel project-panel"><div className="panel-head"><b>Projeler</b><span>{visibleProjects.length} kayıt</span></div><div className="project-list">
          {visibleProjects.map(p=><button key={p.id} className={selected?.id===p.id?"project active":"project"} onClick={()=>{setSelected(p);setActiveDocument(null)}}><span className="code">{p.code}</span><b>{p.name}</b><small>{p.contractingAuthority}</small><ChevronRight/></button>)}
          {!visibleProjects.length&&<p className="empty">Henüz proje yok. İlk projenizi oluşturun.</p>}</div></article>
          <article className="panel document-panel"><div className="panel-head"><div><b>{selected?.name??"Dokümanlar"}</b><span>{documents.length} belge</span></div>{selected&&<button className="link" onClick={()=>loadDocuments()}><RefreshCw/> Durumu yenile</button>}</div>
            {selected?<><form className="upload-form" onSubmit={upload}><UploadCloud/><div><b>Teknik şartname yükle</b><small>PDF veya DOCX · En fazla 100 MB</small></div><select name="type"><option value="TECHNICAL_SPECIFICATION">Teknik şartname</option><option value="ADDENDUM">Zeyilname</option><option value="PRODUCT_CATALOG">Teknik katalog</option></select><input name="file" type="file" accept=".pdf,.docx" required/><button className="primary" disabled={busy}>{busy?"Yükleniyor…":"Yükle"}</button></form>
              <div className="document-list">{documents.map(d=><button key={d.id} className={activeDocument?.id===d.id?"document active":"document"} onClick={()=>setActiveDocument(d)}><FileText/><div><b>{d.name}</b><small>v{d.currentVersion} · {new Date(d.createdAt).toLocaleDateString("tr-TR")}</small></div><span className={`badge ${d.status.toLowerCase()}`}>{d.status.replaceAll("_"," ")}</span></button>)}{!documents.length&&<p className="empty">Bu projeye henüz belge yüklenmedi.</p>}</div>
            </>:<p className="empty">Dokümanlarını görmek için bir proje seçin.</p>}</article></section>
        {activeDocument&&<section className="panel clause-panel"><div className="panel-head"><div><b>Madde ağacı</b><span>{activeDocument.name} · {clauses.length} madde</span></div><div className="panel-actions"><button className="link" onClick={openPreview}><FileText/> Belgeyi aç</button><span className={`badge ${activeDocument.status.toLowerCase()}`}>{activeDocument.status.replaceAll("_"," ")}</span></div></div>
          {activeDocument.status==="READY"?<div className="clause-list">{clauses.map(c=><details key={c.id}><summary><span>{c.number}</span><b>{c.title}</b><small>Sayfa {c.pageNumber}</small></summary><p>{c.sourceText}</p></details>)}</div>:<div className="processing"><RefreshCw className="spin"/><div><b>Belge işleniyor</b><p>Güvenlik taraması, metin çıkarımı ve yapı tespiti devam ediyor.</p></div></div>}</section>}
      </div></main>
    {newProject&&<div className="modal-backdrop"><form className="modal" onSubmit={createProject}><div className="modal-head"><div><p className="eyebrow">YENİ ÇALIŞMA</p><h2>İhale projesi oluştur</h2></div><button type="button" onClick={()=>setNewProject(false)}><X/></button></div><label>Proje adı<input name="name" required maxLength={200}/></label><label>İhaleyi yapan kurum<input name="authority" required maxLength={200}/></label><div className="form-grid"><label>Kayıt numarası<input name="registration" maxLength={100}/></label><label>Son teklif tarihi<input name="deadline" type="date"/></label></div><label>Öncelik<select name="priority" defaultValue="NORMAL"><option value="LOW">Düşük</option><option value="NORMAL">Normal</option><option value="HIGH">Yüksek</option><option value="CRITICAL">Kritik</option></select></label><label>Açıklama<textarea name="description" rows={3} maxLength={4000}/></label><button className="primary large" disabled={busy}>{busy?"Oluşturuluyor…":"Projeyi oluştur"}</button></form></div>}
    {message&&<button className="toast" onClick={()=>setMessage("")}><CheckCircle2/>{message}<X/></button>}
  </div>;
}
