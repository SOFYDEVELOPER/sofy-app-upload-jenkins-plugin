package sofy.jenkins.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;

import hudson.Util;
import org.acegisecurity.AccessDeniedException;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.apache.http.util.EntityUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;


public class TestMobileAppWithSofy extends Recorder {

    private String apiToken;
    private String buildPath;
    private Secret secret;
    private String certName;
    private String applicationGuid;
//    private CreateMobileTestRunResponse testRunResponse;

    @DataBoundConstructor
    public TestMobileAppWithSofy(String apiToken, String apkPath, String certName, String applicationGuid) {
        this.buildPath = apkPath;
        this.apiToken = Secret.fromString(apiToken).getEncryptedValue();
        this.certName = certName;
        this.applicationGuid = applicationGuid;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        try {
            listener.getLogger().println("Preparing to Upload build to Sofy.");
            String buildLocation = build.getWorkspace() + "/" + this.buildPath;
            String testRunInfo = stageMobileTestRun(listener.getLogger(), buildLocation);
//            if (testRunInfo != null && !testRunInfo.isEmpty()) {
//                this.testRunResponse = new ObjectMapper().readValue(testRunInfo.replaceAll("[\\[\\]]", ""), CreateMobileTestRunResponse.class);
//                listener.getLogger().println("Test Run scheduled!");
//            }

        } catch (Exception e) {
            e.printStackTrace();
            listener.getLogger().println("Unable to upload. An error occurred.");
        }
        //build.addAction(new ViewMobileTestRunResults(build, this.testRunResponse, this.apiToken));
        return true;
    }

    private String stageMobileTestRun(PrintStream logger, String buildLocation) throws Exception {
        // http
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost httppost = new HttpPost("https://public.sofy.ai/parser-microservice/build-upload");
        // set Subscription Key
        String subKey = this.apiToken;
        String certificatename = this.certName;
        String applicationguid = this.applicationGuid;
        try {
            subKey = UUID.fromString(Secret.fromString(this.apiToken).getPlainText()).toString();
        } catch (Exception e) {
            e.printStackTrace();
            logger.println("Invalid API Key, unable to Stage test run on Sofy.ai. Please refresh your API Key");
        }
        httppost.setHeader("x-sofy-auth-key", subKey);
        // Upload APK
        File buildHandle = null;
        try {
            buildHandle = new File(new File(buildLocation).getAbsolutePath());
            // path is wrong
            if (!buildHandle.exists()) {
                logger.println("Invalid Build location provided. Provided path does not exist: \"" + buildHandle.getAbsolutePath() + "\"");
                logger.println("Unable to upload build at Sofy.ai");
                return "";
            }

            if (buildHandle.isDirectory()) {
                buildHandle = Arrays.stream(buildHandle.listFiles() != null ? buildHandle.listFiles() : new File[]{})
//                        .filter(file -> file.getName().toLowerCase().endsWith(".apk") || file.getName().toLowerCase().endsWith(".ipa"))
                        .findFirst()
                        .orElse(null);
            }

            if (buildHandle == null  ) {
                logger.println("Invalid Build location provided. No '.apk'  or '.ipa' file found in provided directory: \"" + (buildHandle == null ? "" : buildHandle.getAbsolutePath()) + "\"");
                logger.println("Unable to upload build at Sofy");
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.println("Invalid APK location provided. Unable to upload build at Sofy.ai");
        }

        if (buildHandle != null) {
            logger.println("Uploading build for the following BUILD: \"" + buildHandle.getAbsolutePath() + "\"");
            FileBody fileBodyApk = new FileBody(buildHandle, ContentType.DEFAULT_BINARY);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("applicationFile", fileBodyApk);
            if(certificatename != null && !certificatename.trim().isEmpty())
            {
               builder.addTextBody("CertificateName", certificatename);
            }
            if(applicationguid != null && !applicationguid.trim().isEmpty())
            {
                builder.addTextBody("ApplicationGUID", applicationguid);
            }
            HttpEntity entity = builder.build();
            httppost.setEntity(entity);
            // wait for response
            HttpResponse response = client.execute(httppost);
            HttpEntity entity1 = response.getEntity();
            String responseString = EntityUtils.toString(entity1, "UTF-8");
//            System.out.println(responseString)
            logger.println(responseString);
            return "build_upload";
        }

        return "";
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Symbol("Sofy Upload")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Upload application build to Sofy.ai";
        }

    }


}
