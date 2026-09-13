package com.fairhome.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The username of the officer signed in for this request, used on the audit trail. */
public final class CurrentOfficer {

    private CurrentOfficer() {
    }

    public static String name(FairHomeProperties properties) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return properties.getAdmin().getOfficerName();
    }
}
