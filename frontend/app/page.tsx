"use client";

import {
  Activity, AlertTriangle, ArrowRight, Bell, BookOpen, Boxes, Building2,
  CalendarDays, CheckSquare, ChevronDown, ChevronRight, CircleCheck,
  ClipboardCheck, FileSearch, FolderKanban, LayoutDashboard, ListTodo, Menu,
  MoreHorizontal, Plus, Search, Settings2, ShieldAlert, Sparkles, Users, X,
} from "lucide-react";
import { useMemo, useState } from "react";

const nav = [
  { label: "Ana Panel", icon: LayoutDashboard, active: true },
  { label: "İhale Projeleri", icon: FolderKanban, badge: "12" },
  { label: "Firma Yetkinlikleri", icon: Building2 },
  { label: "Ürün Kataloğu", icon: Boxes },
  { label: "Görevler", icon: ListTodo, badge: "8" },
  { label: "Risk Merkezi", icon: ShieldAlert, badge: "4" },
  { label: "Raporlar", icon: BookOpen },
];

const projects = [
  { code: "TND-2026-0145", name: "Ulusal Veri Merkezi Altyapı İhalesi", org: "Dijital Dönüşüm Ofisi", days: 14, score: 78, risk: "Kritik", owner: "MK", status: "Uzman incelemesinde" },
  { code: "TND-2026-0142", name: "Şehir Hastanesi PACS Modernizasyonu", org: "Sağlık Bakanlığı", days: 7, score: 91, risk: "Orta", owner: "EA", status: "Rapor hazırlanıyor" },
  { code: "TND-2026-0138", name: "Kurumsal Siber Güvenlik Platformu", org: "Enerji A.Ş.", days: 22, score: 64, risk: "Yüksek", owner: "SD", status: "Analiz ediliyor" },
];

const metrics = [
  { label: "Aktif projeler", value: 12, note: "+2 bu ay", icon: FolderKanban, tone: "sky" },
  { label: "Kritik riskli", value: 4, note: "1 yeni risk", icon: AlertTriangle, tone: "rose" },
  { label: "İnceleme bekleyen", value: 38, note: "12 bana atanmış", icon: ClipboardCheck, tone: "amber" },
  { label: "Tamamlanan analiz", value: 27, note: "Son 7 gün", icon: Sparkles, tone: "violet" },
];

export default function Dashboard() {
  const [query, setQuery] = useState("");
  const [menu, setMenu] = useState(false);
  const [toast, setToast] = useState("");
  const filtered = useMemo(() => projects.filter((p) =>
    `${p.code} ${p.name} ${p.org}`.toLocaleLowerCase("tr").includes(query.toLocaleLowerCase("tr"))
  ), [query]);
  const notify = (text: string) => {
    setToast(text);
    window.setTimeout(() => setToast(""), 2400);
  };

  return (
    <div className="stage">
      <div className="ambient orb-a"/><div className="ambient orb-b"/><div className="ambient orb-c"/>
      {menu && <button className="scrim" aria-label="Menüyü kapat" onClick={() => setMenu(false)}/>}
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <div className="ai-orb"><Sparkles size={20}/></div>
          <div><strong>NANObaseAI <Sparkles size={14}/></strong><span>Şartname İnceleme</span></div>
          <button className="close" onClick={() => setMenu(false)}><X size={20}/></button>
        </div>
        <div className="company"><div className="company-logo">NT</div><div><small>Aktif firma</small><b>Nano Teknoloji A.Ş.</b></div><ChevronDown size={16}/></div>
        <nav>
          <p>ÇALIŞMA ALANI</p>
          {nav.map(({label, icon: Icon, badge, active}) => <button key={label} className={active ? "nav-link active" : "nav-link"} onClick={() => notify(`${label} açılıyor`)}>
            <Icon size={19}/><span>{label}</span>{badge && <em>{badge}</em>}{!badge && !active && <ChevronRight size={15}/>}
          </button>)}
          <p>YÖNETİM</p>
          <button className="nav-link" onClick={() => notify("Kullanıcı yönetimi açılıyor")}><Users size={19}/><span>Kullanıcılar ve Roller</span><ChevronRight size={15}/></button>
          <button className="nav-link" onClick={() => notify("Sistem ayarları açılıyor")}><Settings2 size={19}/><span>Sistem Ayarları</span><ChevronRight size={15}/></button>
        </nav>
        <div className="system-card"><div className="mini-orb"><Sparkles size={15}/></div><div><b>AI analiz motoru</b><span><i/> Tüm sistemler çalışıyor</span></div></div>
        <div className="profile"><div className="avatar">MA</div><div><b>Mehmet Aksoy</b><span>İhale Yöneticisi</span></div><MoreHorizontal size={18}/></div>
      </aside>

      <div className="module-shell">
        <header>
          <button className="menu" onClick={() => setMenu(true)} aria-label="Menüyü aç"><Menu size={20}/></button>
          <div className="search"><Search size={17}/><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Proje, kurum, madde veya risk ara…"/><kbd>⌘ K</kbd></div>
          <div className="header-actions"><button onClick={() => notify("Analiz kuyruğunda 3 işlem var")}><Activity size={17}/><span>Analiz kuyruğu</span><b>3</b></button><button onClick={() => notify("5 yeni bildiriminiz var")}><Bell size={18}/><i/></button></div>
        </header>

        <main>
          <section className="hero">
            <div className="hero-aurora"/><div className="hero-grid"/>
            <div className="hero-copy"><p>NANObaseAI · İHALE ZEKÂSI</p><h1>Günaydın, Mehmet</h1><span>Bugün incelemeniz gereken <b>12 madde</b> ve yaklaşan <b>3 son tarih</b> var.</span></div>
            <div className="hero-actions"><span className="live"><i/> Sistem canlı</span><button onClick={() => notify("Yeni proje sihirbazı açılıyor")}><Plus size={17}/> Yeni proje oluştur</button></div>
            <div className="hero-rule"/>
          </section>

          <section className="metrics">
            {metrics.map(({label,value,note,icon:Icon,tone}, index) => <button className={`metric ${tone}`} key={label} style={{animationDelay:`${index*70}ms`}} onClick={() => notify(`${label}: ${value}`)}>
              <div><span>{label}</span><i><Icon size={17}/></i></div><strong>{value}</strong><small><Sparkles size={13}/>{note}<ArrowRight size={14}/></small>
            </button>)}
          </section>

          <section className="content-grid">
            <article className="glass portfolio">
              <div className="card-head"><div><h2>Proje portföyü</h2><p>Aktif projelerin uygunluk ve risk görünümü</p></div><button onClick={() => notify("Tüm projeler açılıyor")}>Tümünü gör <ArrowRight size={14}/></button></div>
              <div className="portfolio-body"><div className="donut"><div><strong>24</strong><span>Toplam proje</span></div></div><div className="legend"><span><i className="v"/>Uzman incelemesinde <b>8</b></span><span><i className="t"/>Analiz ediliyor <b>6</b></span><span><i className="s"/>Rapor hazırlanıyor <b>4</b></span><span><i className="l"/>Diğer <b>6</b></span></div><div className="health"><span>Ortalama uygunluk <b>%79</b></span><div><i/></div><small><CircleCheck size={13}/> Geçen aya göre %6 artış</small></div></div>
            </article>

            <article className="glass deadlines"><div className="card-head"><div><h2>Yaklaşan tarihler</h2><p>Önümüzdeki 30 gün</p></div><CalendarDays size={19}/></div>
              {[["30","TEM","PACS Modernizasyonu","Son teklif tarihi","3 gün"],["03","AĞU","Veri Merkezi Altyapısı","Soru sorma son tarihi","7 gün"],["10","AĞU","Siber Güvenlik Platformu","Teklif hazırlama hedefi","14 gün"]].map((d,i)=><button className="deadline" key={d[2]} onClick={() => notify(`${d[2]} takvim kaydı açıldı`)}><time><b>{d[0]}</b><span>{d[1]}</span></time><p><b>{d[2]}</b><span>{d[3]}</span></p><em className={i===0?"urgent":""}>{d[4]}</em></button>)}
            </article>
          </section>

          <section className="glass project-card">
            <div className="card-head"><div><h2>Öncelikli projeler</h2><p>Yakın tarihli veya yüksek riskli projeler</p></div><div className="mini-search"><Search size={15}/><input value={query} onChange={(e)=>setQuery(e.target.value)} placeholder="Projelerde ara"/></div></div>
            <div className="table-wrap"><table><thead><tr><th>PROJE</th><th>SON TEKLİF</th><th>UYGUNLUK</th><th>RİSK</th><th>SORUMLU</th><th>DURUM</th><th/></tr></thead><tbody>{filtered.map(p=><tr key={p.code}><td><em>{p.code}</em><b>{p.name}</b><span>{p.org}</span></td><td><b>{p.days} gün</b><span>10 Ağustos 2026</span></td><td><div className="score" style={{"--score":`${p.score*3.6}deg`} as React.CSSProperties}><span>{p.score}%</span></div></td><td><mark className={p.risk.toLocaleLowerCase("tr")}>{p.risk}</mark></td><td><div className="avatar small">{p.owner}</div></td><td><mark className="state">{p.status}</mark></td><td><button onClick={()=>notify(`${p.code} açılıyor`)}><MoreHorizontal size={18}/></button></td></tr>)}</tbody></table>{filtered.length===0&&<div className="empty">Eşleşen proje bulunamadı.</div>}</div>
          </section>

          <section className="content-grid bottom">
            <article className="glass"><div className="card-head"><div><h2>Bana atanan görevler</h2><p>Önceliğe göre sıralandı</p></div><CheckSquare size={19}/></div>
              {[["Garanti maddesini doğrula","Ulusal Veri Merkezi","Kritik","Bugün"],["ISO 27001 kanıtını incele","Siber Güvenlik Platformu","Yüksek","Yarın"],["Hukuk görüşünü tamamla","PACS Modernizasyonu","Normal","30 Tem"]].map(t=><button className="task" key={t[0]} onClick={()=>notify(t[0])}><i/><p><b>{t[0]}</b><span>{t[1]}</span></p><em>{t[2]}</em><time>{t[3]}</time></button>)}
            </article>
            <article className="glass"><div className="card-head"><div><h2>Son aktiviteler</h2><p>Ekibinizdeki son gelişmeler</p></div><Activity size={19}/></div>
              <div className="activity"><i className="sky"><FileSearch size={16}/></i><p><b>Teknik şartname analizi tamamlandı</b><span>Veri Merkezi Altyapısı · Ayşe Yılmaz</span></p><time>12 dk</time></div>
              <div className="activity"><i className="rose"><AlertTriangle size={16}/></i><p><b>Kritik risk oluşturuldu</b><span>Siber Güvenlik Platformu · Selin Demir</span></p><time>38 dk</time></div>
              <div className="activity"><i className="teal"><CircleCheck size={16}/></i><p><b>Uygunluk kararı onaylandı</b><span>PACS Modernizasyonu · Emre Arslan</span></p><time>1 sa</time></div>
            </article>
          </section>
        </main>
      </div>
      {toast&&<div className="toast"><CircleCheck size={17}/>{toast}</div>}
    </div>
  );
}
