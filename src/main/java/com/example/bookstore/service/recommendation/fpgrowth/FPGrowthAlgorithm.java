package com.example.bookstore.service.recommendation.fpgrowth;

import java.util.*;

public class FPGrowthAlgorithm {
    private final double minSupport;
    private final double minConfidence;
    private final double minLift;
    private int minSupportCount;
    private int totalTransactions;
    private Map<Long, Integer> singleItemCounts;

    public FPGrowthAlgorithm(double minSupport, double minConfidence, double minLift) {
        this.minSupport = minSupport;
        this.minConfidence = minConfidence;
        this.minLift = minLift;
    }

    public Map<Long, List<AssociationRule>> mineRules(List<List<Long>> transactions) {
        this.totalTransactions = transactions.size();
        this.minSupportCount = Math.max(1, (int) Math.ceil(minSupport * totalTransactions));
        this.singleItemCounts = new HashMap<>();

        // BƯỚC 1: Quét lần 1 - Đếm tần suất các item đơn lẻ (Support)
        for (List<Long> tx : transactions) {
            Set<Long> uniqueItems = new HashSet<>(tx);
            for (Long item : uniqueItems) {
                singleItemCounts.put(item, singleItemCounts.getOrDefault(item, 0) + 1);
            }
        }
        
        // Loại bỏ các sách không đạt minSupport (ít người mua)
        singleItemCounts.entrySet().removeIf(e -> e.getValue() < minSupportCount);

        // BƯỚC 2: Khởi tạo Header Table và FP-Tree
        FPNode root = new FPNode(null, null);
        Map<Long, HeaderTableItem> headerTable = new HashMap<>();
        for (Long item : singleItemCounts.keySet()) {
            headerTable.put(item, new HeaderTableItem(item, singleItemCounts.get(item)));
        }

        // Quét lần 2 - Chèn dữ liệu vào FP-Tree
        for (List<Long> tx : transactions) {
            List<Long> filteredTx = new ArrayList<>();
            for (Long item : new HashSet<>(tx)) {
                if (singleItemCounts.containsKey(item)) filteredTx.add(item);
            }
            if (filteredTx.isEmpty()) continue;

            // Sắp xếp các item trong giỏ hàng theo tần suất giảm dần để tối ưu việc nén cây
            filteredTx.sort((a, b) -> {
                int cmp = Integer.compare(singleItemCounts.get(b), singleItemCounts.get(a));
                return (cmp != 0) ? cmp : a.compareTo(b);
            });

            insertTree(filteredTx, root, headerTable, 1);
        }

        // BƯỚC 3: Khai phá cây (Tìm các cặp thường xuyên - Frequent Pairs)
        Map<Set<Long>, Integer> frequentPairs = new HashMap<>();
        List<Long> sortedItems = new ArrayList<>(headerTable.keySet());
        // Duyệt từ item ít xuất hiện nhất ngược lên
        sortedItems.sort(Comparator.comparingInt(a -> headerTable.get(a).totalCount)); 

        for (Long item : sortedItems) {
            HeaderTableItem headerItem = headerTable.get(item);
            Map<Long, Integer> conditionalPatternBase = new HashMap<>();

            // Lần theo đường dẫn từ các lá (leaf) lên gốc (root)
            FPNode curr = headerItem.head;
            while (curr != null) {
                int pathCount = curr.count;
                FPNode parent = curr.parent;
                while (parent != null && parent.bookId != null) {
                    conditionalPatternBase.put(parent.bookId, conditionalPatternBase.getOrDefault(parent.bookId, 0) + pathCount);
                    parent = parent.parent;
                }
                curr = curr.next;
            }

            // Lọc ra các item đi kèm đạt chuẩn minSupport
            for (Map.Entry<Long, Integer> entry : conditionalPatternBase.entrySet()) {
                if (entry.getValue() >= minSupportCount) {
                    Set<Long> pair = new HashSet<>(Arrays.asList(item, entry.getKey()));
                    frequentPairs.put(pair, entry.getValue());
                }
            }
        }

        // BƯỚC 4: Tính toán Luật kết hợp (Confidence, Lift)
        Map<Long, List<AssociationRule>> rulesMap = new HashMap<>();
        for (Map.Entry<Set<Long>, Integer> entry : frequentPairs.entrySet()) {
            List<Long> pair = new ArrayList<>(entry.getKey());
            if (pair.size() != 2) continue;

            Long itemA = pair.get(0);
            Long itemB = pair.get(1);
            int supportABCount = entry.getValue();

            generateRule(itemA, itemB, supportABCount, rulesMap);
            generateRule(itemB, itemA, supportABCount, rulesMap);
        }

        // Sắp xếp các luật theo Lift (độ nâng) rồi đến Confidence (độ tin cậy)
        for (List<AssociationRule> rules : rulesMap.values()) {
            rules.sort((r1, r2) -> {
                int cmp = Double.compare(r2.getLift(), r1.getLift());
                return cmp != 0 ? cmp : Double.compare(r2.getConfidence(), r1.getConfidence());
            });
        }
        return rulesMap;
    }

    private void insertTree(List<Long> transaction, FPNode root, Map<Long, HeaderTableItem> headerTable, int count) {
        FPNode curr = root;
        for (Long item : transaction) {
            if (curr.children.containsKey(item)) {
                curr.children.get(item).increment(count);
                curr = curr.children.get(item);
            } else {
                FPNode newNode = new FPNode(item, curr);
                newNode.count = count;
                curr.children.put(item, newNode);
                curr = newNode;
                headerTable.get(item).addNode(newNode);
            }
        }
    }

    private void generateRule(Long antecedent, Long consequent, int supportABCount, Map<Long, List<AssociationRule>> rulesMap) {
        double supportA = (double) singleItemCounts.get(antecedent) / totalTransactions;
        double supportB = (double) singleItemCounts.get(consequent) / totalTransactions;
        double supportAB = (double) supportABCount / totalTransactions;

        double confidence = supportAB / supportA;
        if (confidence >= minConfidence) {
            double lift = confidence / supportB;
            if (lift >= minLift) {
                rulesMap.putIfAbsent(antecedent, new ArrayList<>());
                rulesMap.get(antecedent).add(new AssociationRule(antecedent, consequent, supportAB, confidence, lift));
            }
        }
    }
}