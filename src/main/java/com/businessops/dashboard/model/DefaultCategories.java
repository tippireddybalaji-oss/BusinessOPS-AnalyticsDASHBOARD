package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.List;
import java.util.Set;

/**
 * Standard pre-seeded default categories for newly created workspaces.
 *
 * <p>All default categories have deterministic IDs and {@code isDefault = true}.
 * They cannot be renamed or archived by users.
 */
public final class DefaultCategories {

    public static final Set<String> DEFAULT_CATEGORY_IDS = Set.of(
            "def_inc_sales",
            "def_inc_consulting",
            "def_inc_investment",
            "def_inc_freelance",
            "def_inc_other",
            "def_exp_rent",
            "def_exp_salaries",
            "def_exp_software",
            "def_exp_marketing",
            "def_exp_supplies",
            "def_exp_travel",
            "def_exp_taxes",
            "def_exp_other"
    );

    private DefaultCategories() {
        // Utility class
    }

    public static boolean isDefault(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return false;
        }
        return DEFAULT_CATEGORY_IDS.contains(categoryId.trim());
    }

    public static List<Category> createDefaultCategories(String workspaceId, String creatorUid) {
        Timestamp now = Timestamp.now();
        String uid = (creatorUid != null && !creatorUid.isBlank()) ? creatorUid : "system";

        return List.of(
                // Income Categories
                new Category("def_inc_sales", workspaceId, "Client Invoicing / Sales", "INCOME", true,
                        "#10B981", "briefcase", false, null, now, now, uid, uid),
                new Category("def_inc_consulting", workspaceId, "Consulting & Services", "INCOME", true,
                        "#3B82F6", "users", false, null, now, now, uid, uid),
                new Category("def_inc_investment", workspaceId, "Investment & Interest", "INCOME", true,
                        "#8B5CF6", "trending-up", false, null, now, now, uid, uid),
                new Category("def_inc_freelance", workspaceId, "Freelance / Project Contract", "INCOME", true,
                        "#06B6D4", "file-text", false, null, now, now, uid, uid),
                new Category("def_inc_other", workspaceId, "Other Income", "INCOME", true,
                        "#6B7280", "plus-circle", false, null, now, now, uid, uid),

                // Expense Categories
                new Category("def_exp_rent", workspaceId, "Rent & Utilities", "EXPENSE", true,
                        "#EF4444", "home", false, null, now, now, uid, uid),
                new Category("def_exp_salaries", workspaceId, "Salaries & Contractor Wages", "EXPENSE", true,
                        "#F97316", "user-check", false, null, now, now, uid, uid),
                new Category("def_exp_software", workspaceId, "Software & Subscriptions", "EXPENSE", true,
                        "#F59E0B", "cpu", false, null, now, now, uid, uid),
                new Category("def_exp_marketing", workspaceId, "Marketing & Advertising", "EXPENSE", true,
                        "#EC4899", "megaphone", false, null, now, now, uid, uid),
                new Category("def_exp_supplies", workspaceId, "Office Supplies & Equipment", "EXPENSE", true,
                        "#14B8A6", "package", false, null, now, now, uid, uid),
                new Category("def_exp_travel", workspaceId, "Travel & Entertainment", "EXPENSE", true,
                        "#6366F1", "map-pin", false, null, now, now, uid, uid),
                new Category("def_exp_taxes", workspaceId, "Taxes & Compliance", "EXPENSE", true,
                        "#84CC16", "shield-check", false, null, now, now, uid, uid),
                new Category("def_exp_other", workspaceId, "Other Expense", "EXPENSE", true,
                        "#9CA3AF", "minus-circle", false, null, now, now, uid, uid)
        );
    }
}

