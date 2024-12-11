package org.mifos.connector.airtel.dto;

import java.util.Map;
import javax.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Holds data required for handling paybill transactions.
 */
@Component
@ConfigurationProperties(prefix = "paybill")
@Validated
public class PaybillProps {
    private String accountHoldingInstitutionId;
    private String timer;
    private Map<String, AmsProps> amsShortCodes;
    private String defaultShortCode;

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

    /**
     * Get the AMS properties for the given short code. If the short code is null or blank,
     * the AMS properties associated with the default short code is returned.
     *
     * @param shortCode the short code
     * @return the AMS properties
     */
    public AmsProps getAmsProps(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            shortCode = defaultShortCode;
        }
        AmsProps amsProps = amsShortCodes.get(shortCode);
        amsProps.businessShortCode = shortCode;
        return amsProps;
    }

    public String getDefaultShortCode() {
        return defaultShortCode;
    }

    public void setDefaultShortCode(String defaultShortCode) {
        this.defaultShortCode = defaultShortCode;
    }

    @AssertTrue(message = "Default short code is not among the configured AMS short codes.")
    public boolean isDefaultShortCodeValid() {
        return amsShortCodes.containsKey(defaultShortCode);
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
    }
}
