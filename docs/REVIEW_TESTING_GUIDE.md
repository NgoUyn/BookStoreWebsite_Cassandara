# 🚀 Quick Start Guide - Testando a Funcionalidade de Avaliação

## ⚡ Como Testar (Passo a Passo)

### 1️⃣ **Fazer Login como BUYER**
```
URL: http://localhost:8080/main
Login: buyer@test.com / senha123
```

### 2️⃣ **Comprar um Livro**
- Adicionar livro ao carrinho
- Checkout
- Confirmar pagamento (VNPay mock)
- Confirmar entrega (order status = COMPLETED)

### 3️⃣ **Ir para Página de Detalhes do Livro**
```
URL: http://localhost:8080/main/product/{bookId}
```

### 4️⃣ **Testar Funcionalidades**

#### A. Visualizar Avaliações Existentes
- ✅ Vê "Avaliação média" (ex: 4.5 / 5)
- ✅ Vê número total de reviews
- ✅ Botões de filtro por estrelas (Tất Cả, 5 Sao, 4 Sao, etc)
- ✅ Lista de reviews com:
  - Nome do avaliador
  - Número de estrelas
  - Data
  - Comentário

#### B. Criar Nova Avaliação
- ✅ Clicar nas estrelas para selecionar rating (1-5)
- ✅ Escrever comentário (máx 2000 chars)
- ✅ Clicar "Gửi đánh giá"
- ✅ Ver confirmação: "Cảm ơn bạn đã đánh giá sản phẩm!"
- ✅ Review aparece na lista com badge "Đánh giá của bạn"

#### C. Editar Avaliação
- ✅ Clicar botão "Chỉnh sửa" no seu review
- ✅ Form pré-preenchido com dados anteriores
- ✅ Alterar rating ou comentário
- ✅ Clicar "Cập nhật đánh giá"
- ✅ Ver mudanças refletidas

#### D. Deletar Avaliação
- ✅ Clicar botão "Xóa" no seu review
- ✅ Confirmar exclusão
- ✅ Review removido da lista
- ✅ Stats atualizados

#### E. Filtrar por Estrelas
- ✅ Clicar "5 Sao" → Mostra apenas 5-star reviews
- ✅ Clicar "4 Sao" → Mostra apenas 4-star reviews
- ✅ Clicar "Tất Cả" → Mostra todas

---

## 🧪 Testing via API (com Postman/cURL)

### 1. **Listar Reviews de um Livro**
```bash
curl -X GET "http://localhost:8080/api/reviews/book/1?page=0&size=10"
```

### 2. **Obter Estatísticas**
```bash
curl -X GET "http://localhost:8080/api/reviews/book/1/stats"
```

### 3. **Obter Distribuição de Ratings**
```bash
curl -X GET "http://localhost:8080/api/reviews/book/1/distribution"
```

### 4. **Filtrar por Rating**
```bash
curl -X GET "http://localhost:8080/api/reviews/book/1/by-rating/5?page=0&size=10"
```

### 5. **Criar Review** (Autenticado)
```bash
curl -X POST "http://localhost:8080/api/reviews" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": 1,
    "rating": 5,
    "comment": "Quyển sách rất hay!"
  }'
```

### 6. **Checar Status de Review do Usuário** (Autenticado)
```bash
curl -X GET "http://localhost:8080/api/reviews/book/1/user-review" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 7. **Listar Meus Reviews** (Autenticado)
```bash
curl -X GET "http://localhost:8080/api/reviews/my-reviews?page=0&size=10" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 8. **Editar Review** (Autenticado)
```bash
curl -X PUT "http://localhost:8080/api/reviews/1" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 4,
    "comment": "Sửa lại bình luận"
  }'
```

### 9. **Deletar Review** (Autenticado)
```bash
curl -X DELETE "http://localhost:8080/api/reviews/1" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 🔍 Expected Behaviors

### ✅ Permissões Corretas
- [ ] Não-logado: Pode ver reviews e stats (público)
- [ ] Logado SELLER: Não pode criar review
- [ ] Logado BUYER (sem compra): Erro "Phải mua sách"
- [ ] Logado BUYER (com compra): Pode criar review
- [ ] Owner: Pode editar/deletar próprio review
- [ ] Outro usuário: Não pode editar/deletar review alheio

### ✅ Validações
- [ ] Rating < 1 ou > 5: Erro 400
- [ ] Comentário > 2000 chars: Erro 400
- [ ] Criar 2º review: Erro "Đã đánh giá rồi"
- [ ] Sem auth: Erro 401

### ✅ UI/UX
- [ ] Stars brilham ao passar mouse
- [ ] Rating selecionado permanece após selecionar
- [ ] Form limpa após enviar
- [ ] Badges aparecem (Đã mua hàng, Đánh giá của bạn)
- [ ] Botões Edit/Delete aparecem apenas no próprio review
- [ ] Distribuição atualiza em tempo real
- [ ] Paginação funciona

---

## 📊 Database Queries (para verificação)

```sql
-- Ver todos os reviews de um livro
SELECT * FROM book_reviews WHERE book_id = 1 AND is_hidden = 0 ORDER BY created_at DESC;

-- Ver distribuição de ratings
SELECT rating, COUNT(*) as count FROM book_reviews 
WHERE book_id = 1 AND is_hidden = 0 
GROUP BY rating;

-- Ver reviews de um usuário
SELECT * FROM book_reviews WHERE user_id = 1 AND is_hidden = 0;

-- Ver se usuário já avaliou um livro
SELECT * FROM book_reviews WHERE book_id = 1 AND user_id = 1;

-- Calcular média
SELECT AVG(rating) as avg_rating FROM book_reviews 
WHERE book_id = 1 AND is_hidden = 0;
```

---

## 🐛 Troubleshooting

### "Bạn chỉ có thể đánh giá những cuốn sách đã mua..."
- ❌ Problema: Usuário não comprou o livro ou pedido não está COMPLETED
- ✅ Solução: Fazer um novo pedido e confirmar entrega

### "Bạn đã đánh giá cuốn sách này rồi."
- ❌ Problema: Tentando criar 2º review para mesmo livro
- ✅ Solução: Editar o review existente ou deletar e criar novo

### Botões "Chỉnh sửa" e "Xóa" não aparecem
- ❌ Problema: Vendo review de outro usuário
- ✅ Solução: Esses botões só aparecem para seus próprios reviews

### Erro 401 Unauthorized
- ❌ Problema: Token JWT expirado ou não enviado
- ✅ Solução: Fazer login novamente

### Erro 403 Forbidden
- ❌ Problema: Tentando editar/deletar review de outra pessoa
- ✅ Solução: Operação não permitida

---

## 📱 Mobile Testing

- [ ] Testado em mobile (responsivo)
- [ ] Scrolling suave
- [ ] Stars tocáveis em touch screen
- [ ] Botões acessíveis
- [ ] Form legível em small screens

---

## ✨ Nice-to-Have Features (Futuro)

- [ ] Upload de fotos com review
- [ ] Helpful votes ("Bình luận này có hữu ích?")
- [ ] Seller responses
- [ ] Email notifications
- [ ] Review search/filter avançado
- [ ] Trending reviews

---

## 📞 Support

Se encontrar algum erro, verifique:
1. Console do navegador (F12) - Ver erros de JS
2. Network tab - Ver request/response HTTP
3. Backend logs - Ver stack traces
4. Banco de dados - Ver se dados foram salvos

---

**Last Updated:** May 28, 2026
**Status:** ✅ Ready for Testing
