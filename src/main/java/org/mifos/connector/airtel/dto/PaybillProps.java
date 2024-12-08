package org.mifos.connector.airtel.dto;

import static org.mifos.connector.airtel.camel.config.CamelProperties.DEFAULT;

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
    private String timer;
    private Map<String, AmsProps> amsShortCodes;

    public String getAccountHoldingInstitutionId() {
        return accountHoldingInstitutionId;
    }

    public void setAccountHoldingInstitutionId(String accountHoldingInstitutionId) {
        this.accountHoldingInstitutionId = accountHoldingInstitutionId;
    }

    public String getTimer() {
        return timer;
    }

    public void setTimer(String timer) {
        this.timer = timer;
    }

    public Map<String, AmsProps> getAmsShortCodes() {
        return amsShortCodes;
    }

    public void setAmsShortCodes(Map<String, AmsProps> amsShortCodes) {
        this.amsShortCodes = amsShortCodes;
    }

    public AmsProps getAmsProps(String shortCode) {
        return amsShortCodes.getOrDefault(shortCode, amsShortCodes.get(DEFAULT));
    }

    /**
     * Holds the relevant AMS properties.
     */
    public static class AmsProps {
        private String amsName;
        private String amsUrl;
        private String identifier;
        private String businessShortCode;

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

        public String getBusinessShortCode() {
            return businessShortCode;
        }

        public void setBusinessShortCode(String businessShortCode) {
            this.businessShortCode = businessShortCode;
        }
    }
}
