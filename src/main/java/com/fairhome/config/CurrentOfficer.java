package com.fairhome.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The username of the officer signed in for this request, used on the audit trail. */
public final class CurrentOfficer {

    private static final Logger log = LoggerFactory.getLogger(CurrentOfficer.class);

    private CurrentOfficer() {
    }

    public static String name(FairHomeProperties properties) {
        log.debug("FairHome : CurrentOfficer : in method name : START");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String officer;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            officer = authentication.getName();
        } else {
            officer = properties.getAdmin().getOfficerName();
        }
        log.debug("FairHome : CurrentOfficer : in method name : END");
        return officer;
    }
}
