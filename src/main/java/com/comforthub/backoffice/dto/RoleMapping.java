package com.comforthub.backoffice.dto;

/**
 * Bridges the UI-facing role vocabulary ("Worker"/"Owner") and the role strings
 * actually stored on {@code users.role}. The backend stores "Worker" for
 * workers and "Merchant"/"Admin" for owners;
 * {@link com.comforthub.backoffice.service.CurrentUserService} maps
 * "Merchant"/"Admin" to the OWNER role. An invited "Owner" is therefore stored
 * as "Merchant" to stay consistent with how owners are created elsewhere
 * (see {@code MeController#selectCompany}).
 */
public final class RoleMapping {

    private RoleMapping() {
    }

    /** UI role -&gt; stored role. Null / blank / unrecognised defaults to "Worker". */
    public static String toStored(String uiRole) {
        return isOwnerRole(uiRole) ? "Merchant" : "Worker";
    }

    /** Stored role -&gt; UI role. "Merchant" / "Admin" / "Owner" -&gt; "Owner", else "Worker". */
    public static String toDisplay(String storedRole) {
        return isOwnerRole(storedRole) ? "Owner" : "Worker";
    }

    private static boolean isOwnerRole(String role) {
        if (role == null) {
            return false;
        }
        String r = role.trim();
        return "Owner".equalsIgnoreCase(r) || "Merchant".equalsIgnoreCase(r) || "Admin".equalsIgnoreCase(r);
    }
}
