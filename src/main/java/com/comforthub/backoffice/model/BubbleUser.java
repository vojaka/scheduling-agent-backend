package com.comforthub.backoffice.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BubbleUser {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("name")
    @JsonAlias({"name", "name_text"})
    private String name;

    @JsonProperty("role")
    @JsonAlias({"role", "role_list_option___user_role", "primary_login_role_option___user_role"})
    private Object roleObject;

    @JsonIgnore
    public String getRole() {
        if (roleObject == null) {
            return "Worker";
        }
        if (roleObject instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) roleObject;
            if (list.isEmpty()) {
                return "Worker";
            }
            return list.get(0).toString();
        }
        return roleObject.toString();
    }

    public void setRole(Object role) {
        this.roleObject = role;
    }

    @JsonProperty("maxHours")
    @JsonAlias({"maxHours", "maxHours_number", "maxhours_number"})
    private Integer maxHours;

    @JsonProperty("active")
    @JsonAlias({"active", "active_boolean"})
    private Boolean active;

    // The user's "Representing a Company" company id (scoping key).
    // NOTE: verify the exact Bubble JSON key against a /user API response;
    // if none of these aliases match, company_id stays null and must be
    // backfilled by SQL (see V2__add_user_scoping.sql).
    @JsonProperty("representing_a_company")
    @JsonAlias({
        "representing_a_company",
        "representing_a_company_custom____merchant",
        "representing_a_company_custom_merchant",
        "company",
        "company_custom____merchant"
    })
    private String companyId;
}
