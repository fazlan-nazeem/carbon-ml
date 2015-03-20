/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.ml.analysis.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.json.JSONObject;
import org.junit.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.ml.integration.common.utils.MLIntegrationBaseTest;
import org.wso2.carbon.ml.integration.common.utils.MLIntegrationTestConstants;
import org.wso2.carbon.ml.integration.common.utils.exception.MLIntegrationBaseTestException;

public class GetAnalysisTestCase extends MLIntegrationBaseTest {
    
    private static final String DatasetName = "SampleDataForGetAnalysisTestCase";
    private static final String projectName = "TestProjectForGetAnalysisTestcase";
    private static final String analysisName = "TestProjectForGetAnalysisTestcase";
    private static int projectId;

    @BeforeClass(alwaysRun = true, groups = "wso2.ml.integration")
    public void initTest() throws Exception {
        super.init();
        uploadDatasetFromCSV(DatasetName, "1.0", "data/fcSample.csv");
        createProject(projectName, DatasetName);
        CloseableHttpResponse response = doHttpGet(new URI(getServerUrlHttps() + "/api/projects/" + projectName));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        JSONObject responseJson = new JSONObject(bufferedReader.readLine());
        projectId = responseJson.getInt("id");
        response.close();
        bufferedReader.close();
        createAnalysis(analysisName, projectId);
    }

    /**
     * Test retrieving all analyzes.
     * 
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     * @throws MLIntegrationBaseTestException 
     */
    @Test(groups = "wso2.ml.integration", description = "Get all analyzes")
    public void testGetAllAnalyzes() throws ClientProtocolException, IOException, URISyntaxException,
            MLIntegrationBaseTestException {
        CloseableHttpResponse response = doHttpGet(new URI(getServerUrlHttps() + "/api/analyzes/"));
        Assert.assertEquals(MLIntegrationTestConstants.HTTP_OK, response.getStatusLine().getStatusCode());
    }
    /**
     * Test retrieving an analysis by name.
     * 
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     * @throws MLIntegrationBaseTestException 
     */
    @Test(groups = "wso2.ml.integration", description = "Get an analysis by name", dependsOnMethods = "testGetAllAnalyzes")
    public void testGetAnalysis() throws ClientProtocolException, IOException, URISyntaxException, 
            MLIntegrationBaseTestException {
        CloseableHttpResponse response = doHttpGet(new URI(getServerUrlHttps() + "/api/analyzes/" + analysisName));
        Assert.assertEquals(MLIntegrationTestConstants.HTTP_OK, response.getStatusLine().getStatusCode());
    }
}