# Dinamik Business Roller

Keycloak platform rolleri authentication ve temel admin sınırını korur. Workflow iş
rolleri ise `business_role` ve `user_business_role` tablolarındadır.

Rolün kodu, adı, scope’u ve JSON attributes alanı tenant tarafından yönetilebilir.
Atama; proje, entity veya global scope, geçerlilik başlangıç/bitiş zamanı ve user
kimliğiyle çözülür. Yeni hukuk/teknik/kalite rolü Java enum’u veya frontend build
gerektirmez.

Authorization iki katmandır: platform permission işlemi yapma yetkisini, business
role ise ilgili workflow node’u veya assignment adaylığını belirler. Kullanıcı kendi
rolünü veya reviewer kaydını normal görev API’sinden değiştiremez.

Business role yönetim UI/API’si bu sprintin merkez ekranında bulunmaz; veri modeli ve
policy resolution hazırdır. Tenant admin CRUD yüzeyi sonraki iterasyondur.
