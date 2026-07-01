package com.comforthub.backoffice.service;

import java.util.Optional;

/**
 * Dispatches the account-setup invitation for a freshly invited worker / owner
 * and returns the resulting Auth0 user id (the JWT {@code sub}) when known, so
 * it can be stored on the pending membership row.
 *
 * <p>Implementations MUST be best-effort: a failure to dispatch the invite
 * (e.g. Auth0 unreachable, or not configured at all) must not prevent the
 * membership row from being persisted — the owner can re-send later.
 */
public interface WorkerInvitationService {

    /**
     * Provision / look up the Auth0 user for {@code email} and email them an
     * account-setup (password-change) link.
     *
     * @param email     the invitee's email address (already validated)
     * @param name      the invitee's display name, may be {@code null}
     * @param companyId the company the invitee is being added to (for context)
     * @return the Auth0 user id ({@code sub}) if known, otherwise empty — e.g.
     *         when the Auth0 Management API is not configured or the call failed
     */
    Optional<String> invite(String email, String name, String companyId);
}
