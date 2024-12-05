package org.mifos.connector.airtel.dto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Holds data required for handling paybill transactions.
 */
@Component
@ConfigurationProperties(prefix = "paybill")
public class PaybillProps {
    private String accountHoldingInstitutionId;
    private Map<String, AmsProps> amsShortCodes;

    public String getAccountHoldingInstitutionId() {
        return accountHoldingInstitutionId;
    }

    public void setAccountHoldingInstitutionId(String accountHoldingInstitutionId) {
        this.accountHoldingInstitutionId = accountHoldingInstitutionId;
    }

    public Map<String, AmsProps> getAmsShortCodes() {
        return amsShortCodes;
    }

    public void setAmsShortCodes(Map<String, AmsProps> amsShortCodes) {
        this.amsShortCodes = amsShortCodes;
    }

    /**
     * Holds the relevant AMS properties.
     */
    public static class AmsProps {
        private String amsName;
        private String amsUrl;
        private String identifier;

        public String getAmsName() {
            return amsName;
        }

        public void setAmsName(String amsName) {
            this.amsName = amsName;
        }

        public String getAmsUrl() {
            return amsUrl;
        }

        public void setAmsUrl(String amsUrl) {
            this.amsUrl = amsUrl;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
    }
}
