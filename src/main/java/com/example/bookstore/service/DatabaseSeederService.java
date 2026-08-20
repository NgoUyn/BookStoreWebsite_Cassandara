package com.example.bookstore.service;

import com.example.bookstore.dto.SeedRequest;
import com.example.bookstore.dto.SeedResult;
import com.example.bookstore.model.*;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.service.cluster.CustomerAnalysisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseSeederService {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SellerShopRepository sellerShopRepository;
    private final SellerShopService sellerShopService;
    private final GeminiService geminiService;
    private final CustomerAnalysisService customerAnalysisService;
    private final com.example.bookstore.repository.OrderRepository orderRepository;
    private final com.example.bookstore.repository.SubOrderRepository subOrderRepository;
    private final com.example.bookstore.repository.OrderItemRepository orderItemRepository;

    @Value("${app.seeder.max-books:300}")
    private int defaultMaxBooks;

    @Value("${app.seeder.ai-enabled:false}")
    private boolean defaultAiEnabled;

    @Transactional
    public SeedResult seedData(SeedRequest request) {
        SeedRequest options = request != null ? request : new SeedRequest();

        boolean seedCategories = getOrDefault(options.getSeedCategories(), true);
        boolean seedUsers = getOrDefault(options.getSeedUsers(), true);
        boolean seedBooks = getOrDefault(options.getSeedBooks(), true);
        boolean includeAi = getOrDefault(options.getIncludeAi(), defaultAiEnabled);

        int maxBooks = options.getMaxBooks() != null && options.getMaxBooks() > 0
                ? options.getMaxBooks()
                : defaultMaxBooks;

        SeedResult result = new SeedResult();
        result.setWarnings(new ArrayList<>());

        log.info("Seeding started (categories={}, users={}, books={}, maxBooks={}, ai={})",
                seedCategories, seedUsers, seedBooks, maxBooks, includeAi);

        List<Category> categories = seedCategories ? seedCategories(result) : categoryRepository.findAll();
        if (categories.isEmpty()) {
            result.getWarnings().add("No categories available; book seeding skipped.");
        }

        List<User> sellers = seedUsers ? seedUsers(result) : userRepository.findAllByRole(UserRole.SELLER);
        if (sellers.isEmpty()) {
            result.getWarnings().add("No sellers available; book seeding skipped.");
        }

        List<Book> newBooks = List.of();
        if (seedBooks && !categories.isEmpty() && !sellers.isEmpty()) {
            newBooks = readAndSaveFromCsv(categories, sellers, maxBooks, result);
        } else if (seedBooks) {
            result.getWarnings().add("Skipped book seeding because categories or sellers are missing.");
        }

        if (includeAi) {
            if (geminiService.isEnabled()) {
                result.setAiEnqueued(enrichBooksWithAI(newBooks));
            } else {
                result.setAiEnqueued(false);
                result.getWarnings().add("Gemini API key missing; AI enrichment skipped.");
            }
        }

        log.info("Seeding finished (categoriesAdded={}, categoriesUpdated={}, usersAdded={}, booksAdded={}, booksSkipped={})",
                result.getCategoriesAdded(), result.getCategoriesUpdated(), result.getUsersAdded(),
                result.getBooksAdded(), result.getBooksSkipped());

        // Auto-seed a sample order for buyer@gmail.com to help verify buyer dashboard
        try {
            User buyer = userRepository.findByUsername("buyer@gmail.com");
            if (buyer != null && orderRepository.count() == 0) {
                List<Book> books = bookRepository.findAll();
                if (!books.isEmpty()) {
                    seedSampleOrderForBuyer(buyer, books.get(0));
                }
            }
        } catch (Exception e) {
            log.warn("Auto sample order seeding failed: {}", e.getMessage());
        }

        return result;
    }

    @Transactional
    public void seedSampleOrderForBuyer(User buyer, Book sampleBook) {
        if (buyer == null || sampleBook == null) return;
        if (orderRepository.count() > 0) return;

        Order order = Order.builder()
                .buyer(buyer)
                .shippingAddress(buyer.getShopAddress() != null ? buyer.getShopAddress() : "Địa chỉ mẫu")
                .shippingFee(30000.0)
                .discountAmount(0.0)
                .couponCode(null)
                .totalAmount(sampleBook.getPrice() + 30000.0)
                .build();

        SubOrder sub = SubOrder.builder()
                .parentOrder(order)
                .seller(sampleBook.getSeller())
                .status(com.example.bookstore.model.enums.OrderStatus.SHIPPING)
                .subTotal(sampleBook.getPrice())
                .build();

        com.example.bookstore.model.OrderItem item = com.example.bookstore.model.OrderItem.builder()
                .subOrder(sub)
                .book(sampleBook)
                .unitPrice(sampleBook.getPrice())
                .quantity(1)
                .build();

        sub.setItems(List.of(item));
        order.setSubOrders(List.of(sub));

        orderRepository.save(order);
    }

    private boolean getOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private List<Category> seedCategories(SeedResult result) {
        List<Category> orderedCategories = new ArrayList<>();
        List<Category> seededCategories = List.of(
                new Category(null, "Tiểu thuyết - Văn học", "Tác phẩm văn học trong và ngoài nước", null),
                new Category(null, "Tâm lý - Kỹ năng sống", "Sách phát triển bản thân", null),
                new Category(null, "Kinh tế - Quản lý", "Kiến thức kinh doanh và tài chính", null),
                new Category(null, "Sách Thiếu nhi", "Truyện cổ tích, truyện tranh", null),
                new Category(null, "Lịch sử - Địa lý", "Tìm hiểu về thế giới và nhân loại", null),
                new Category(null, "Khoa học - Viễn tưởng", "Sách về khoa học khám phá", null),
                new Category(null, "Truyện tranh (Manga/Comic)", "Các bộ truyện tranh nổi tiếng", null),
                new Category(null, "Ngoại ngữ", "Tài liệu học tiếng Anh, Nhật, Hàn", null)
        );

        Map<String, Category> existingCategoriesMap = new HashMap<>();
        try {
            for (Category existing : categoryRepository.findAll()) {
                existingCategoriesMap.put(normalizeForComparison(existing.getName()), existing);
            }
        } catch (Exception e) {
            result.getWarnings().add("Failed to load existing categories: " + e.getMessage());
        }

        int added = 0;
        int updated = 0;

        for (Category seed : seededCategories) {
            String seedNorm = normalizeForComparison(seed.getName());
            Category existing = existingCategoriesMap.get(seedNorm);

            if (existing == null) {
                try {
                    existing = categoryRepository.save(seed);
                    added++;
                } catch (Exception e) {
                    result.getWarnings().add("Failed to add category: " + seed.getName());
                    continue;
                }
            } else {
                boolean isNameDiff = existing.getName() == null || !existing.getName().equals(seed.getName());
                boolean isDescDiff = existing.getDescription() == null || !existing.getDescription().equals(seed.getDescription());

                if (isNameDiff || isDescDiff) {
                    existing.setName(seed.getName());
                    existing.setDescription(seed.getDescription());
                    try {
                        existing = categoryRepository.save(existing);
                        updated++;
                    } catch (Exception e) {
                        result.getWarnings().add("Failed to update category: " + seed.getName());
                    }
                }
            }
            orderedCategories.add(existing);
        }

        result.setCategoriesAdded(added);
        result.setCategoriesUpdated(updated);
        return orderedCategories;
    }

    private List<User> seedUsers(SeedResult result) {
        AtomicInteger added = new AtomicInteger(0);

        ensureUser("admin@gmail.com", UserRole.ADMIN, "admin123", null, null, added);
        ensureUser("buyer@gmail.com", UserRole.BUYER, "Buyer@123", null, null, added);
        User sellerNhaNam = ensureUser(
                "shop_nha_nam@gmail.com",
                UserRole.SELLER,
                "seller123",
                "Nha Nam Official",
                "Quang Trung Software Park, HCMC",
                added
        );
        User sellerTre = ensureUser(
                "shop_tre@gmail.com",
                UserRole.SELLER,
                "seller123",
                "NXB Tre Official",
                "District 3, HCMC",
                added
        );

        // Create SellerShop records for seeded sellers if they don't already exist
        if (sellerNhaNam != null) {
            ensureSellerShop(sellerNhaNam, "Nha Nam Official");
        }
        if (sellerTre != null) {
            ensureSellerShop(sellerTre, "NXB Tre Official");
        }

        result.setUsersAdded(added.get());
        return List.of(sellerNhaNam, sellerTre).stream().filter(u -> u != null).toList();
    }

    private User ensureUser(
            String username,
            UserRole role,
            String rawPassword,
            String shopName,
            String shopAddress,
            AtomicInteger added
    ) {
        User existing = userRepository.findByUsername(username);
        if (existing != null) {
            return existing;
        }

        User.UserBuilder builder = User.builder()
                .username(username)
                .passwordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt(10)))
                .role(role);

        if (shopName != null) {
            builder.shopName(shopName);
        }
        if (shopAddress != null) {
            builder.shopAddress(shopAddress);
        }

        User saved = userRepository.save(builder.build());
        added.incrementAndGet();

        // Tự động phân tích churn cho user seed (gọi Python ML API)
        try {
            customerAnalysisService.analyzeCustomer(saved.getId());
            log.info("Đã phân tích churn cho seed user: {}", saved.getUsername());
        } catch (Exception e) {
            log.warn("Không thể phân tích churn cho seed user {}: {}", saved.getUsername(), e.getMessage());
        }

        return saved;
    }

    /**
     * Ensure SellerShop record exists for a seller user
     * Creates SellerShop if it doesn't exist, with a unique slug generated from shop name
     */
    private void ensureSellerShop(User seller, String shopName) {
        if (seller == null || seller.getRole() != UserRole.SELLER) {
            return;
        }

        // Check if SellerShop already exists for this seller
        if (sellerShopRepository.findBySellerId(seller.getId()).isPresent()) {
            return;
        }

        // Generate unique slug using SellerShopService
        String slug = sellerShopService.generateUniqueSlug(shopName);

        // Create new SellerShop with all default values
        SellerShop newSellerShop = SellerShop.builder()
                .seller(seller)
                .slug(slug)
                .shopName(shopName)
                .description("Cửa hàng sách chính thức của " + shopName)
                .address(seller.getShopAddress() != null ? seller.getShopAddress() : shopName + " - Main Store")
                .city("Ho Chi Minh")
                .province("Ho Chi Minh")
                .contactEmail(seller.getUsername())
                .contactPhone("")
                .approvalStatus(ApprovalStatus.PENDING)
                .build();

        sellerShopRepository.save(newSellerShop);
    }

    private List<Book> readAndSaveFromCsv(
            List<Category> categories,
            List<User> sellers,
            int maxBooks,
            SeedResult result
    ) {
        List<Book> addedBooks = new ArrayList<>();
        java.io.InputStream is = getClass().getResourceAsStream("/Books.csv");
        if (is == null) {
            result.getWarnings().add("Books.csv not found in resources.");
            return addedBooks;
        }

        Set<String> existingBookKeys = new HashSet<>();
        for (Book existing : bookRepository.findAll()) {
            existingBookKeys.add(bookKey(existing.getTitle(), existing.getAuthor()));
        }

        int added = 0;
        int skipped = 0;
        Random random = new Random();
        Category[] categoryArray = categories.toArray(new Category[0]);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                if (isFirstLine) { isFirstLine = false; continue; }
                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                if (data.length < 8) {
                    skipped++;
                    continue;
                }

                String title = data[1].replace("\"", "").trim();
                String author = data[2].replace("\"", "").trim();
                String uniqueKey = bookKey(title, author);
                if (existingBookKeys.contains(uniqueKey)) {
                    skipped++;
                    continue;
                }

                Book book = new Book();
                book.setTitle(title);
                book.setAuthor(author);
                String publishYearRaw = data[3].replace("\"", "").trim();
                try {
                    book.setPublishYear(Integer.parseInt(publishYearRaw));
                } catch (NumberFormatException ex) {
                    book.setPublishYear(null);
                }
                book.setPublisher(data[4].replace("\"", "").trim());
                book.setImageUrl(data[7].replace("\"", "").trim());

                double randomPrice = Math.round((Math.random() * 200000) + 50000) / 1000 * 1000;
                book.setPrice(randomPrice);
                book.setStockQuantity((int)(Math.random() * 90) + 10);
                book.setApprovalStatus(ApprovalStatus.APPROVED);

                User seller = sellers.get(random.nextInt(sellers.size()));
                book.setSeller(seller);
                book.setCategory(categoryArray[random.nextInt(categoryArray.length)]);
                book.setDescription(buildFallbackDescription(book));

                bookRepository.save(book);
                existingBookKeys.add(uniqueKey);
                addedBooks.add(book);
                added++;

                if (added >= maxBooks) {
                    break;
                }
            }
        } catch (Exception e) {
            result.getWarnings().add("Failed to read Books.csv: " + e.getMessage());
        }

        result.setBooksAdded(added);
        result.setBooksSkipped(skipped);
        return addedBooks;
    }

    private boolean enrichBooksWithAI(List<Book> books) {
        if (books == null || books.isEmpty()) {
            return false;
        }

        for (Book book : books) {
            geminiService.generateDescription(book.getTitle(), book.getAuthor())
                    .thenAccept(description -> {
                        if (description == null || description.isBlank()) {
                            return;
                        }
                        book.setDescription(description.trim() + " (Mo ta boi AI)");
                        bookRepository.save(book);
                        log.info("Updated AI description for: {}", book.getTitle());
                    });
        }
        return true;
    }

    private String buildFallbackDescription(Book book) {
        return String.format(
                "%s la mot tua sach cua %s, phu hop cho ban doc muon mo rong kien thuc va trai nghiem doc sach chat luong. Phien ban hien tai dang duoc phan phoi boi he thong BOOKOM.",
                book.getTitle(),
                book.getAuthor()
        );
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeForComparison(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String bookKey(String title, String author) {
        return normalizeKey(title) + "::" + normalizeKey(author);
    }
}