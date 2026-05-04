package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Product, Long> {

    /**
     * S5-F10: Calculates net revenue per category.
     * Logic: (Sum of completed transaction amounts) - (Sum of refunds).
     * COALESCE is used to ensure categories with no refunds return 0 instead of NULL.
     */
    @Query(value = "SELECT p.category, (SUM(t.amount) - COALESCE(SUM(r.refund_amount), 0)) AS net_revenue " +
                   "FROM products p " +
                   "JOIN transactions t ON p.id = t.product_id " +
                   "LEFT JOIN refunds r ON t.id = r.transaction_id " +
                   "WHERE t.status = 'COMPLETED' " +
                   "GROUP BY p.category", nativeQuery = true)
    List<Object[]> getCategoryRevenueData();
}