# Document Change Sets

`POST /api/v1/documents/{documentId}/change-sets` aynı tenant ve aynı dokümana
ait iki sürümü alır. `PolicyDocumentChangeMatcher` clause number, content hash,
token similarity ve sort metadata ile yapısal eşleştirme yapar.

Change type concept’leri (`addedConceptCode`, `removedConceptCode`,
`modifiedConceptCode`, `unchangedConceptCode`) ve minimum similarity impact
policy’sindedir. Sonuç `document_change_set` ve `document_change_item` olarak
saklanır; önceki document/version fiziksel olarak silinmez.

UI eski/yeni clause ID’lerini gösterir. Uzman yanlış eşleşmede iki tarafı
düzeltebilir; düzeltme audit edilir. Unchanged item’lar impact traversal
başlangıcı yapılmaz.
