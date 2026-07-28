# Workflow Simulation

`WorkflowSimulationService`, production instance oluşturmadan sürüm graph’ını örnek
context üzerinde gezer. Sonuç `workflow_simulation_run` tablosuna input snapshot,
finding listesi ve geçerlilik ile yazılır.

Kontroller:

- Ulaşılamayan node ve dead-end.
- Eksik veya çakışan transition.
- Visit limitini aşan olası döngü.
- Yetkilendirilmeyen node listesi.
- Terminal/finalization yolunun bulunması.
- Handler/assignment configuration bulunabilirliği.

API:

```text
POST /api/v1/workflow-versions/{versionId}/simulate
```

UI “Validate ve simulate” işlemiyle draft sürümü kaydeder, simulation finding’lerini
gösterir ve yalnız geçerli sonuçta aktivasyon butonunu açar.

Unit testte parallel gateway ile iki dalın ziyaret edildiği doğrulanır. Gerçek
tenant verisi, kullanıcı kapasitesi ve tatil takvimiyle yük/performance simulation
bu hostta çalıştırılmadı.
