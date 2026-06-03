package com.rcacopilot.llm;

import java.util.List;

public class MockLlmClient implements LlmClient {

    @Override
    public String summarize(String diagnosticInfo) throws Exception {
        String infoLower = diagnosticInfo.toLowerCase();
        
        if (infoLower.contains("11001") || infoLower.contains("winsock")) {
            return "The DatacenterHubOutboundProxyProbe has failed on the backend machine due to a WinSock error 11001 indicating that the host is unknown. " +
                   "The total UDP socket count is 15276, with the majority being used by the Transport.exe process, indicating hub port exhaustion.";
        }
        if (infoLower.contains("mailboxofflineexception") || infoLower.contains("deliveryhang")) {
            return "The Mailbox delivery service is hanging. The mailbox queue size has exceeded thresholds, and logs report MailboxOfflineException. " +
                   "Requires immediate service check.";
        }
        if (infoLower.contains("tenantsettingsnotfoundexception") || infoLower.contains("invalidjournaling")) {
            return "Messages stuck in the submission queue for a long time. Tenant transport configuration settings contain invalid properties, " +
                   "throwing TenantSettingsNotFoundException.";
        }
        if (infoLower.contains("invalid certificate") || infoLower.contains("authcertissue")) {
            return "Tokens for requesting services were not able to be created. Services report outage. A previous invalid certificate overrode the " +
                   "existing one due to misconfiguration.";
        }
        if (infoLower.contains("disk is full") || infoLower.contains("fulldisk")) {
            return "Many processes crashed and threw IO exceptions. System reports a specific disk space is completely full at 100% usage.";
        }
        
        return "Incident reports generic telemetry abnormalities. Logs indicate minor service failures across the forest. " +
               "Further diagnostics show general stack anomalies.";
    }

    @Override
    public String predictRootCause(String targetSummary, List<String> options) throws Exception {
        String targetLower = targetSummary.toLowerCase();

        String predictedCategory = "Unseen incident";
        String optionLetter = "A";

        char currentLetter = 'B';
        for (String option : options) {
            String optionLower = option.toLowerCase();
            if (targetLower.contains("hubportexhaustion") || targetLower.contains("11001")) {
                if (optionLower.contains("hubportexhaustion")) {
                    predictedCategory = "HubPortExhaustion";
                    optionLetter = String.valueOf(currentLetter);
                }
            } else if (targetLower.contains("deliveryhang") || targetLower.contains("mailboxofflineexception")) {
                if (optionLower.contains("deliveryhang")) {
                    predictedCategory = "DeliveryHang";
                    optionLetter = String.valueOf(currentLetter);
                }
            } else if (targetLower.contains("invalidjournaling") || targetLower.contains("tenantsettingsnotfound")) {
                if (optionLower.contains("invalidjournaling")) {
                    predictedCategory = "InvalidJournaling";
                    optionLetter = String.valueOf(currentLetter);
                }
            } else if (targetLower.contains("authcertissue") || targetLower.contains("invalid certificate")) {
                if (optionLower.contains("authcertissue")) {
                    predictedCategory = "AuthCertIssue";
                    optionLetter = String.valueOf(currentLetter);
                }
            } else if (targetLower.contains("fulldisk") || targetLower.contains("disk is full")) {
                if (optionLower.contains("fulldisk")) {
                    predictedCategory = "FullDisk";
                    optionLetter = String.valueOf(currentLetter);
                }
            }
            currentLetter++;
        }

        // Return a mock Chain-of-Thought text response similar to what Llama 3 would output
        return String.format(
                "Reasoning:\n" +
                "1. Analyzing the target incident summary: '%s'\n" +
                "2. Comparing against nearest neighbors: Options contain category matches.\n" +
                "3. Finding: The characteristics match option %s.\n\n" +
                "Selected Option: %s\n" +
                "Predicted Category: %s\n" +
                "Explanation: The incident matches option %s because both describe identical symptoms and telemetry logs.",
                targetSummary, optionLetter, optionLetter, predictedCategory, optionLetter
        );
    }
}
