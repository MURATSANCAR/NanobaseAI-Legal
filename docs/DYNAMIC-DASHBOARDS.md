# Dinamik Dashboard

Dashboard’lar `dashboard_definition`, `dashboard_version` ve `dashboard_widget`
tablolarıyla versiyonlanır. Widget type, data source, display, görünürlük condition ve
grid position JSON/concept verisidir.

Sprint 7 operations dashboard’ı
`GET /api/v1/ui-configurations/dashboard/SPRINT_7_OPERATIONS` üzerinden yüklenir.
Frontend kart listesini veya kolonlarını sabitlemez; widget response’unu map eder.
Task grid de ayrı backend configuration endpoint’inden gelir.

Widget data source yalnız izinli entity/metric isimleriyle sunulmalıdır. Görünürlük
sunucuda yetkiyle filtrelenir; frontend gizleme güvenlik sınırı değildir.

Bu sprint read/render ve versioned persistence sağlar. Tenant admin dashboard
editor’ünün write API’si ve drag/drop layout yönetimi henüz uygulanmadı.
