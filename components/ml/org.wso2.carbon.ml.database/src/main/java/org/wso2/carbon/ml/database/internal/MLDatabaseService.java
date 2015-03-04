/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.ml.database.internal;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.ml.commons.domain.*;
import org.wso2.carbon.ml.database.DatabaseService;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;
import org.wso2.carbon.ml.database.internal.constants.SQLQueries;
import org.wso2.carbon.ml.database.internal.ds.LocalDatabaseCreator;

import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public class MLDatabaseService implements DatabaseService {

    private static final Log logger = LogFactory.getLog(MLDatabaseService.class);
    private MLDataSource dbh;
    private static final String DB_CHECK_SQL = "SELECT * FROM ML_PROJECT";

    public MLDatabaseService() {
        try {
            dbh = new MLDataSource();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        String value = System.getProperty("setup");
        if (value != null) {
            LocalDatabaseCreator databaseCreator = new LocalDatabaseCreator(dbh.getDataSource());
            try {
                if (!databaseCreator.isDatabaseStructureCreated(DB_CHECK_SQL)) {
                    databaseCreator.createRegistryDatabase();
                } else {
                    logger.info("Machine Learner database already exists. Not creating a new database.");
                }
            } catch (Exception e) {
                String msg = "Error in creating the Machine Learner database";
                throw new RuntimeException(msg, e);
            }
        }

    }

    /**
     * Retrieves the path of the value-set having the given ID, from the
     * database.
     *
     * @param valueSetId Unique Identifier of the value-set
     * @return Absolute path of a given value-set
     * @throws DatabaseHandlerException
     */
    public String getValueSetUri(long valueSetId) throws DatabaseHandlerException {

        Connection connection = null;
        ResultSet result = null;
        PreparedStatement getStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            getStatement = connection.prepareStatement(SQLQueries.GET_VALUE_SET_LOCATION);
            getStatement.setLong(1, valueSetId);
            result = getStatement.executeQuery();
            if (result.first()) {
                return result.getNString(1);
            } else {
                logger.error("Invalid value set ID: " + valueSetId);
                throw new DatabaseHandlerException("Invalid value set ID: " + valueSetId);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while reading the Value set " +
                    valueSetId + " from the database: " + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getStatement, result);
        }
    }

    /**
     * @param name
     * @param tenantID
     * @param username
     * @param comments
     * @param sourceType
     * @param targetType
     * @param dataType
     * @throws DatabaseHandlerException
     */
    public void insertDatasetDetails(String name, int tenantID, String username, String comments,
                                     String sourceType, String targetType, String dataType)
            throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the data-set details to the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_DATASET);
            insertStatement.setString(1, name);
            insertStatement.setInt(2, tenantID);
            insertStatement.setString(3, username);
            insertStatement.setString(4, comments);
            insertStatement.setString(5, sourceType);
            insertStatement.setString(6, targetType);
            insertStatement.setString(7, dataType);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the details of data set");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting details of dataset " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public long getDatasetId(String datasetName, int tenantId) throws DatabaseHandlerException {

        Connection connection = null;
        ResultSet result = null;
        PreparedStatement statement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement(SQLQueries.GET_DATASET_ID);
            statement.setString(1, datasetName);
            statement.setInt(2, tenantId);
            result = statement.executeQuery();
            if (result.first()) {
                return result.getLong(1);
            } else {
                throw new DatabaseHandlerException(
                        "No dataset id associated with dataset name: " + datasetName + " and tenant id:" + tenantId);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    " An error has occurred while extracting dataset name: " + datasetName + " and tenant id:" + tenantId);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, statement, result);
        }
    }

    @Override
    public void insertDatasetVersionDetails(long datasetId, int tenantId, String username, String version) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the data-set-version details to the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_DATASET_VERSION);
            insertStatement.setLong(1, datasetId);
            insertStatement.setInt(2, tenantId);
            insertStatement.setString(3, username);
            insertStatement.setString(4, version);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the details of data set version");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting details of dataset version " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertFeatureDefaults(long datasetVersionId, String featureName, String type, int featureIndex, String summary)
            throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the data-set details to the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_FEATURE_DEFAULTS);
            insertStatement.setLong(1, datasetVersionId);
            insertStatement.setString(2, featureName);
            insertStatement.setString(3, type);
            insertStatement.setInt(4, featureIndex);
            insertStatement.setString(5, summary);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the details of feature defaults");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting details of feature defaults " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertValueSet(long datasetVersionId, String name, int tenantId, String username, String uri, SamplePoints samplePoints) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_VALUE_SET);
            insertStatement.setLong(1, datasetVersionId);
            insertStatement.setString(2, name);
            insertStatement.setInt(3, tenantId);
            insertStatement.setString(4, username);
            insertStatement.setString(5, uri);
            insertStatement.setObject(6, samplePoints);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the value set");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting value set " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertDataSource(long valuesetId, int tenantId, String username, String key, String value) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_DATA_SOURCE);
            insertStatement.setLong(1, valuesetId);
            insertStatement.setInt(2, tenantId);
            insertStatement.setString(3, username);
            insertStatement.setString(4, key);
            insertStatement.setString(6, value);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the data source");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting data source " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public long getDatasetVersionId(long datasetId, String datasetVersion) throws DatabaseHandlerException {

        Connection connection = null;
        ResultSet result = null;
        PreparedStatement statement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement(SQLQueries.GET_DATASET_VERSION_ID);
            statement.setLong(1, datasetId);
            statement.setString(2, datasetVersion);
            result = statement.executeQuery();
            if (result.first()) {
                return result.getLong(1);
            } else {
                throw new DatabaseHandlerException(
                        "No dataset id associated with dataset id: " + datasetId + " and version:" + datasetVersion);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    " An error has occurred while extracting dataset id: " + datasetId + " and version:" + datasetVersion);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, statement, result);
        }
    }

    /**
     * Update the value-set table with a value-set sample.
     *
     * @param valueSet       Unique Identifier of the value-set
     * @param valueSetSample SamplePoints object of the value-set
     * @throws DatabaseHandlerException
     */
    public void updateValueSetSample(long valueSet, SamplePoints valueSetSample)
            throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            updateStatement = connection.prepareStatement(SQLQueries.UPDATE_SAMPLE_POINTS);
            updateStatement.setObject(1, valueSetSample);
            updateStatement.setLong(2, valueSet);
            updateStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the sample of value set " + valueSet);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while updating the sample " +
                    "points of value set " + valueSet + ": " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    /**
     * Returns data points of the selected sample as coordinates of three
     * features, needed for the scatter plot.
     *
     * @param valueSetId     Unique Identifier of the value-set
     * @param xAxisFeature   Name of the feature to use as the x-axis
     * @param yAxisFeature   Name of the feature to use as the y-axis
     * @param groupByFeature Name of the feature to be grouped by (color code)
     * @return A JSON array of data points
     * @throws DatabaseHandlerException
     */
    public JSONArray getScatterPlotPoints(long valueSetId, String xAxisFeature, String yAxisFeature,
                                          String groupByFeature) throws DatabaseHandlerException {

        // Get the sample from the database.
        SamplePoints sample = getValueSetSample(valueSetId);

        // Converts the sample to a JSON array.
        List<List<String>> columnData = sample.getSamplePoints();
        Map<String, Integer> dataHeaders = sample.getHeader();
        JSONArray samplePointsArray = new JSONArray();
        int firstFeatureColumn = dataHeaders.get(xAxisFeature);
        int secondFeatureColumn = dataHeaders.get(yAxisFeature);
        int thirdFeatureColumn = dataHeaders.get(groupByFeature);
        for (int row = 0; row < columnData.get(thirdFeatureColumn).size(); row++) {
            if (!columnData.get(firstFeatureColumn).get(row).isEmpty() &&
                    !columnData.get(secondFeatureColumn).get(row).isEmpty() &&
                    !columnData.get(thirdFeatureColumn).get(row).isEmpty()) {
                JSONArray point = new JSONArray();
                point.put(Double.parseDouble(columnData.get(firstFeatureColumn).get(row)));
                point.put(Double.parseDouble(columnData.get(secondFeatureColumn).get(row)));
                point.put(columnData.get(thirdFeatureColumn).get(row));
                samplePointsArray.put(point);
            }
        }

        return samplePointsArray;
    }

    /**
     * Returns sample data for selected features
     *
     * @param valueSetId          Unique Identifier of the value-set
     * @param featureListString String containing feature name list
     * @return A JSON array of data points
     * @throws DatabaseHandlerException
     */
    public JSONArray getChartSamplePoints(long valueSetId, String featureListString) throws DatabaseHandlerException {

        // Get the sample from the database.
        SamplePoints sample = getValueSetSample(valueSetId);

        // Converts the sample to a JSON array.
        List<List<String>> columnData = sample.getSamplePoints();
        Map<String, Integer> dataHeaders = sample.getHeader();
        JSONArray samplePointsArray = new JSONArray();

        // split categoricalFeatureListString String into a String array
        String[] featureList = featureListString.split(",");

        // for each row in a selected categorical feature, iterate through all features
        for (int row = 0; row < columnData.get(dataHeaders.get(featureList[0])).size(); row++) {

            JSONObject point = new JSONObject();
            // for each categorical feature in same row put value into a point(JSONObject)
            // {"Soil_Type1":"0","Soil_Type11":"0","Soil_Type10":"0","Cover_Type":"4"}
            for (int featureCount = 0; featureCount < featureList.length; featureCount++) {
                point.put(featureList[featureCount],
                        columnData.get(dataHeaders.get(featureList[featureCount])).get(row));
            }
            samplePointsArray.put(point);
        }
        return samplePointsArray;
    }

    /**
     * Retrieve the SamplePoints object for a given value-set.
     *
     * @param valueSetId Unique Identifier of the value-set
     * @return SamplePoints object of the value-set
     * @throws DatabaseHandlerException
     */
    private SamplePoints getValueSetSample(long valueSetId) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        ResultSet result = null;
        SamplePoints samplePoints = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            updateStatement = connection.prepareStatement(SQLQueries.GET_SAMPLE_POINTS);
            updateStatement.setLong(1, valueSetId);
            result = updateStatement.executeQuery();
            if (result.first()) {
                samplePoints = (SamplePoints) result.getObject(1);
            }
            return samplePoints;
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while retrieving the sample of " +
                    "value set " + valueSetId + ": " + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement, result);
        }
    }

    /**
     * Returns a set of features in a given range, from the alphabetically ordered set
     * of features, of a data-set.
     *
     * @param datasetID        Unique Identifier of the data-set
     * @param startIndex       Starting index of the set of features needed
     * @param numberOfFeatures Number of features needed, from the starting index
     * @return A list of Feature objects
     * @throws DatabaseHandlerException
     */
    public List<FeatureSummary> getFeatures(String datasetID, String modelId, int startIndex,
                                            int numberOfFeatures) throws DatabaseHandlerException {
        List<FeatureSummary> features = new ArrayList<FeatureSummary>();
        Connection connection = null;
        PreparedStatement getFeatues = null;
        ResultSet result = null;
        try {
            // Create a prepared statement and retrieve data-set configurations.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            getFeatues = connection.prepareStatement(SQLQueries.GET_FEATURES);
            getFeatues.setString(1, datasetID);
            getFeatues.setString(2, datasetID);
            getFeatues.setString(3, modelId);
            getFeatues.setInt(4, numberOfFeatures);
            getFeatues.setInt(5, startIndex);
            result = getFeatues.executeQuery();
            while (result.next()) {
                String featureType = FeatureType.NUMERICAL;
                if (FeatureType.CATEGORICAL.toString().equalsIgnoreCase(result.getString(3))) {
                    featureType = FeatureType.CATEGORICAL;
                }
                // Set the impute option
                String imputeOperation = ImputeOption.DISCARD;
                if (ImputeOption.REPLACE_WTH_MEAN.equalsIgnoreCase(result.getString(5))) {
                    imputeOperation = ImputeOption.REPLACE_WTH_MEAN;
                } else if (ImputeOption.REGRESSION_IMPUTATION.equalsIgnoreCase(
                        result.getString(5))) {
                    imputeOperation = ImputeOption.REGRESSION_IMPUTATION;
                }
                String featureName = result.getString(1);
                boolean isImportantFeature = result.getBoolean(4);
                String summaryStat = result.getString(2);

                features.add(new FeatureSummary(featureName, isImportantFeature, featureType,
                        imputeOperation, summaryStat));
            }
            return features;
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while retrieving features of " +
                    "the data set: " + datasetID + ": " + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getFeatues, result);
        }
    }

    /**
     * This method extracts and returns default features available in a given dataset version
     *
     * @param datasetVersionId The dataset version id
     * @return A list of FeatureSummary
     * @throws DatabaseHandlerException
     */
    @Override
    public List<FeatureSummary> getDefaultFeatures(long datasetVersionId, int startIndex, int numberOfFeatures)
            throws DatabaseHandlerException {
        List<FeatureSummary> features = new ArrayList<FeatureSummary>();
        Connection connection = null;
        PreparedStatement getDefaultFeatues = null;
        ResultSet result = null;

        try {
            connection = dbh.getDataSource().getConnection();
            getDefaultFeatues = connection.prepareStatement(SQLQueries.GET_DEFAULT_FEATURES);

            getDefaultFeatues.setLong(1, datasetVersionId);
            getDefaultFeatues.setInt(2, numberOfFeatures);
            getDefaultFeatues.setInt(3, startIndex);

            result = getDefaultFeatues.executeQuery();

            while (result.next()) {
                String featureType = FeatureType.NUMERICAL;
                if (FeatureType.CATEGORICAL.equalsIgnoreCase(result.getString(3))) {
                    featureType = FeatureType.CATEGORICAL;
                }
                // Set the impute option
                String imputeOperation = ImputeOption.REPLACE_WTH_MEAN;

                String featureName = result.getString(1);

                // Setting up default include flag
                boolean isImportantFeature = true;

                String summaryStat = result.getString(2);

                features.add(new FeatureSummary(featureName, isImportantFeature, featureType, imputeOperation,
                        summaryStat));
            }
            return features;
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while retrieving features of " + "the data set version: "
                    + datasetVersionId + ": " + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getDefaultFeatues, result);
        }
    }

    /**
     * Returns the names of the features of the model
     *
     * @param modelId Unique identifier of the current model
     * @return A list of feature names
     * @throws DatabaseHandlerException
     */
    public List<String> getFeatureNames(long modelId) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement getFeatureNamesStatement = null;
        ResultSet result = null;
        List<String> featureNames = new ArrayList<String>();
        try {
            connection = dbh.getDataSource().getConnection();

            // Create a prepared statement and retrieve model configurations
            getFeatureNamesStatement = connection.prepareStatement(SQLQueries.GET_FEATURE_NAMES);
            getFeatureNamesStatement.setLong(1, modelId);

            result = getFeatureNamesStatement.executeQuery();
            // Convert the result in to a string array to e returned.
            while (result.next()) {
                featureNames.add(result.getString(1));
            }
            return featureNames;
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while retrieving feature "
                    + "names of the dataset for model: " + modelId + ": " + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getFeatureNamesStatement, result);
        }
    }

    /**
     * Retrieve and returns the Summary statistics for a given feature of a
     * given data-set version, from the database.
     *
     * @param datasetVersionId Unique identifier of the data-set version
     * @param featureName      Name of the feature of which summary statistics are needed
     * @return JSON string containing the summary statistics
     * @throws DatabaseHandlerException
     */
    public String getSummaryStats(long datasetVersionId, String featureName)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement getSummaryStatement = null;
        ResultSet result = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            getSummaryStatement = connection.prepareStatement(SQLQueries.GET_SUMMARY_STATS);
            getSummaryStatement.setString(1, featureName);
            getSummaryStatement.setLong(2, datasetVersionId);
            result = getSummaryStatement.executeQuery();
            result.first();
            return result.getString(1);
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while retrieving summary " +
                    "statistics for the feature \"" + featureName + "\" of the data set version " +
                    datasetVersionId + ": " + e.getMessage(), e);
        } finally {
            // Close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, getSummaryStatement, result);
        }
    }

    /**
     * Returns the number of features of a given data-set version
     *
     * @param datasetVersionId Unique identifier of the data-set version
     * @return Number of features in the data-set version
     * @throws DatabaseHandlerException
     */
    public int getFeatureCount(long datasetVersionId) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement getFeatues = null;
        ResultSet result = null;
        int featureCount = 0;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            // Create a prepared statement and extract data-set configurations.
            getFeatues = connection.prepareStatement(SQLQueries.GET_FEATURE_COUNT);
            getFeatues.setLong(1, datasetVersionId);
            result = getFeatues.executeQuery();
            if (result.first()) {
                featureCount = result.getInt(1);
            }
            return featureCount;
        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    "An error occurred while retrieving feature count of the dataset version " + datasetVersionId +
                            ": " + e.getMessage(), e);
        } finally {
            // Close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, getFeatues, result);
        }
    }

    @Override
    public void createProject(String projectName, String description, int tenantId, String username)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement createProjectStatement = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            createProjectStatement = connection.prepareStatement(SQLQueries.CREATE_PROJECT_NEW);
            createProjectStatement.setString(1, projectName);
            createProjectStatement.setString(2, description);
            createProjectStatement.setInt(3, tenantId);
            createProjectStatement.setString(4, username);
            createProjectStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted details of project: " + projectName);
            }
        } catch (SQLException e) {
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while inserting details of project: " + projectName +
                    " to the database: " + e.getMessage(), e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, createProjectStatement);
        }
    }

    /**
     * Delete details of a given project from the database.
     *
     * @param projectId Unique identifier for the project
     * @throws DatabaseHandlerException
     */
    public void deleteProject(String projectId) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement deleteProjectStatement = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            deleteProjectStatement = connection.prepareStatement(SQLQueries.DELETE_PROJECT);
            deleteProjectStatement.setString(1, projectId);
            deleteProjectStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully deleted the project: " + projectId);
            }
        } catch (SQLException e) {
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while deleting the project: " + projectId + ": " +
                    e.getMessage(), e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, deleteProjectStatement);
        }
    }

    @Override
    public void insertAnalysis(long projectId, String name, int tenantId, String username, String comments) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the analysis to the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_ANALYSIS);
            insertStatement.setLong(1, projectId);
            insertStatement.setString(2, name);
            insertStatement.setInt(3, tenantId);
            insertStatement.setString(4, username);
            insertStatement.setString(5, comments);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the analysis");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting analysis " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public long getAnalysisId(int tenantId, String userName, String analysisName) throws DatabaseHandlerException {

        Connection connection = null;
        ResultSet result = null;
        PreparedStatement statement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement(SQLQueries.GET_ANALYSIS_ID);
            statement.setString(1, analysisName);
            statement.setInt(2, tenantId);
            statement.setString(3,userName);
            result = statement.executeQuery();
            if (result.first()) {
                return result.getLong(1);
            } else {
                throw new DatabaseHandlerException(
                        "No analysis id associated with analysis name: " + analysisName + ", tenant id:" + tenantId
                                + " and username:" + userName);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    " An error has occurred while extracting analysis id for analysis name: " + analysisName
                            + ", tenant id:" + tenantId + " and username:" + userName);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, statement, result);
        }
    }

    @Override
    public void insertModel(long analysisId, long valueSetId, int tenantId, String outputModel, String username) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the model to the database
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_MODEL);
            insertStatement.setLong(1, analysisId);
            insertStatement.setLong(2, valueSetId);
            insertStatement.setInt(3, tenantId);
            insertStatement.setString(4, outputModel);
            insertStatement.setString(5, username);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the model");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting model " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertModelConfiguration(long modelId, String key, String value, String type) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the model configuration to the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_MODEL_CONFIGURATION);
            insertStatement.setLong(1, modelId);
            insertStatement.setString(2, key);
            insertStatement.setString(3, value);
            insertStatement.setString(4, type);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the model configuration");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting model configuration " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertHyperParameter(long modelId, String name, int tenantId, String value, String lastModifiedUser) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the hyper parameter to the database
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_HYPER_PARAMETER);
            insertStatement.setLong(1, modelId);
            insertStatement.setString(2, name);
            insertStatement.setInt(3, tenantId);
            insertStatement.setString(4, value);
            insertStatement.setString(5, lastModifiedUser);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the hyper parameter");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting hyper parameter " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    @Override
    public void insertFeatureCustomized(long modelId, int tenantId, String featureName, String imputeOption, boolean inclusion, String lastModifiedUser) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        try {
            // Insert the feature-customized to the database
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            insertStatement = connection.prepareStatement(SQLQueries.INSERT_FEATURE_CUSTOMIZED);
            insertStatement.setLong(1, modelId);
            insertStatement.setInt(2, tenantId);
            insertStatement.setString(3, featureName);
            insertStatement.setString(4, imputeOption);
            insertStatement.setBoolean(5, inclusion);
            insertStatement.setString(6 ,lastModifiedUser);
            insertStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted the feature-customized");
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while inserting feature-customized " +
                            " to the database: " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement);
        }
    }

    /**
     * Assign a tenant to a given project.
     *
     * @param tenantID  Unique identifier for the current tenant.
     * @param projectID Unique identifier for the project.
     * @throws DatabaseHandlerException
     */
    public void addTenantToProject(int tenantID, String projectID)
            throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement addTenantStatement = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            addTenantStatement = connection.prepareStatement(SQLQueries.ADD_TENANT_TO_PROJECT);
            addTenantStatement.setInt(1, tenantID);
            addTenantStatement.setString(2, projectID);
            addTenantStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully added the tenant: " + tenantID + " to the project: " + projectID);
            }
        } catch (SQLException e) {
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while adding the tenant " + tenantID + " to the project "
                    + projectID + ": " + e.getMessage(), e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, addTenantStatement);
        }
    }

    /**
     * Get the project names and created dates, that a tenant is assigned to.
     *
     * @param tenantID Unique identifier for the tenant.
     * @return An array of project ID, Name and the created date of the projects
     *         associated with a given tenant.
     * @throws DatabaseHandlerException
     */
    public String[][] getTenantProjects(int tenantID) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement getTenantProjectsStatement = null;
        ResultSet result = null;
        String[][] projects = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            getTenantProjectsStatement = connection.prepareStatement(SQLQueries.GET_TENANT_PROJECTS,
                    ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            getTenantProjectsStatement.setInt(1, tenantID);
            result = getTenantProjectsStatement.executeQuery();
            // create a 2-d string array having the size of the result set
            result.last();
            int size = result.getRow();
            if (size > 0) {
                projects = new String[size][4];
                result.beforeFirst();
                // put the result set to the string array
                for (int i = 0; i < size; i++) {
                    result.next();
                    projects[i][0] = result.getObject(1).toString();
                    projects[i][1] = result.getString(2);
                    projects[i][2] = result.getDate(3).toString();
                    projects[i][3] = result.getString(4);
                }
            }
            return projects;
        } catch (SQLException e) {
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while retrieving the projects of user " + tenantID + ": "
                    + e.getMessage(), e);
        } finally {
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, getTenantProjectsStatement, result);
        }
    }

    /**
     * Retrieve Details of a Project (Name, Description, Tenant-id, Username, Created-time)
     *
     * @param projectId Unique identifier of the project
     * @return DatabaseHandlerException
     */
    @Override
    public String[] getProject(String projectId) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement deleteProjectStatement = null;
        ResultSet result = null;
        String ProjectDetails[] = new String[3];
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            deleteProjectStatement = connection.prepareStatement(SQLQueries.GET_PROJECT);
            deleteProjectStatement.setString(1, projectId);
            result = deleteProjectStatement.executeQuery();
            if (result.first()) {
                ProjectDetails[0] = result.getString(1);
                ProjectDetails[1] = result.getString(2);
                ProjectDetails[2] = result.getString(3);
                ProjectDetails[3] = result.getString(4);
                ProjectDetails[4] = result.getString(5);
                return ProjectDetails;
            } else {
                throw new DatabaseHandlerException("Invalid project Id: " + projectId);
            }
        } catch (SQLException e) {
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while retrieving the project: " + projectId + ": " +
                    e.getMessage(), e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, deleteProjectStatement, result);
        }
    }

    /**
     * Update the database with all the summary statistics of the sample.
     *
     * @param datasetVersionId Unique Identifier of the data-set
     * @param headerMap        Array of names of features
     * @param type             Array of data-types of each feature
     * @param graphFrequencies List of Maps containing frequencies for graphs, of each feature
     * @param missing          Array of Number of missing values in each feature
     * @param unique           Array of Number of unique values in each feature
     * @param descriptiveStats Array of descriptiveStats object of each feature
     * @param include          Default value to set for the flag indicating the feature is an input or not
     * @throws DatabaseHandlerException
     */
    public void updateSummaryStatistics(long datasetVersionId, Map<String, Integer> headerMap, String[] type,
                                        List<SortedMap<?, Integer>> graphFrequencies, int[] missing, int[] unique,
                                        List<DescriptiveStatistics> descriptiveStats, Boolean include)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            JSONArray summaryStat;
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            int columnIndex;
            for (Map.Entry<String, Integer> columnNameMapping : headerMap.entrySet()) {
                columnIndex = columnNameMapping.getValue();
                // Get the JSON representation of the column summary.
                summaryStat = createJson(type[columnIndex], graphFrequencies.get(columnIndex), missing[columnIndex],
                        unique[columnIndex], descriptiveStats.get(columnIndex));
                // Put the values to the database table. If the feature already exists, updates
                // the row. If not, inserts as a new row.
                //updateStatement = connection.prepareStatement(SQLQueries.UPDATE_SUMMARY_STATS);
                updateStatement = connection.prepareStatement(SQLQueries.INSERT_FEATURE_DEFAULTS);
                updateStatement.setLong(1, datasetVersionId);
                updateStatement.setString(2, columnNameMapping.getKey());
                updateStatement.setString(3, type[columnIndex].toString());
                updateStatement.setInt(4, columnIndex);
                updateStatement.setString(5, summaryStat.toString());
                updateStatement.execute();
            }
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the summary statistics for dataset version " + datasetVersionId);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while updating the database " +
                    "with summary statistics of the dataset " + datasetVersionId + ": " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    @Override
    public void updateSummaryStatistics(long datasetVersionId, SummaryStats summaryStats) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            JSONArray summaryStatJson;
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            int columnIndex;
            for (Map.Entry<String, Integer> columnNameMapping : summaryStats.getHeaderMap().entrySet()) {
                columnIndex = columnNameMapping.getValue();
                // Get the JSON representation of the column summary.
                summaryStatJson = createJson(summaryStats.getType()[columnIndex], summaryStats.getGraphFrequencies().get(columnIndex),
                        summaryStats.getMissing()[columnIndex], summaryStats.getUnique()[columnIndex],
                        summaryStats.getDescriptiveStats().get(columnIndex));

                updateStatement = connection.prepareStatement(SQLQueries.INSERT_FEATURE_DEFAULTS);
                updateStatement.setLong(1, datasetVersionId);
                updateStatement.setString(2, columnNameMapping.getKey());
                updateStatement.setString(3, summaryStats.getType()[columnIndex]);
                updateStatement.setInt(4, columnIndex);
                updateStatement.setString(5, summaryStatJson.toString());
                updateStatement.execute();
            }
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the summary statistics for dataset version " + datasetVersionId);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while updating the database " +
                    "with summary statistics of the dataset " + datasetVersionId + ": " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    /**
     * Create the JSON string with summary statistics for a column.
     *
     * @param type             Data-type of the column
     * @param graphFrequencies Bin frequencies of the column
     * @param missing          Number of missing values in the column
     * @param unique           Number of unique values in the column
     * @param descriptiveStats DescriptiveStats object of the column
     * @return JSON representation of the summary statistics of the column
     */
    private JSONArray createJson(String type, SortedMap<?, Integer> graphFrequencies,
                                 int missing, int unique, DescriptiveStatistics descriptiveStats) {

        JSONObject json = new JSONObject();
        JSONArray freqs = new JSONArray();
        Object[] categoryNames = graphFrequencies.keySet().toArray();
        // Create an array with intervals/categories and their frequencies.
        for (int i = 0; i < graphFrequencies.size(); i++) {
            JSONArray temp = new JSONArray();
            temp.put(categoryNames[i].toString());
            temp.put(graphFrequencies.get(categoryNames[i]));
            freqs.put(temp);
        }
        // Put the statistics to a json object
        json.put("unique", unique);
        json.put("missing", missing);

        DecimalFormat decimalFormat = new DecimalFormat("#.###");
        if (descriptiveStats.getN() != 0) {
            json.put("mean", decimalFormat.format(descriptiveStats.getMean()));
            json.put("median", decimalFormat.format(descriptiveStats.getPercentile(50)));
            json.put("std", decimalFormat.format(descriptiveStats.getStandardDeviation()));
            if (type.equalsIgnoreCase(FeatureType.NUMERICAL)) {
                json.put("skewness", decimalFormat.format(descriptiveStats.getSkewness()));
            }
        }
        json.put("values", freqs);
        json.put("bar", true);
        json.put("key", "Frequency");
        JSONArray summaryStatArray = new JSONArray();
        summaryStatArray.put(json);
        return summaryStatArray;
    }

    /**
     * Set the default values for feature properties of a given model
     *
     * @param datasetVersionId Unique identifier of the data-set-version
     * @param modelId          Unique identifier of the current model
     * @throws DatabaseHandlerException
     */
    public void setDefaultFeatureSettings(long datasetVersionId, long modelId)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        PreparedStatement getDefaultFeatureSettings = null;
        ResultSet result = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(true);
            // read default feature settings from feature_defaults table
            getDefaultFeatureSettings = connection.prepareStatement(SQLQueries.GET_DEFAULT_FEATURE_SETTINGS);
            getDefaultFeatureSettings.setLong(1, datasetVersionId);
            result = getDefaultFeatureSettings.executeQuery();
            // insert default feature settings into feature_customized table
            connection.setAutoCommit(false);
            while (result.next()) {
                insertStatement = connection.prepareStatement(SQLQueries.INSERT_FEATURE_CUSTOMIZED);
                insertStatement.setLong(1, modelId);
                insertStatement.setString(2, result.getString(1));
                insertStatement.setString(3, result.getString(2));
                insertStatement.setString(4, result.getString(4));
                insertStatement.setString(5, ImputeOption.REPLACE_WTH_MEAN);
                insertStatement.setBoolean(6, true); //TODO inclusion
                insertStatement.setString(7, "");    //TODO last modified user
                insertStatement.setDate(8, null);    //TODO last modified time
                insertStatement.execute();
                connection.commit();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully inserted feature defaults of dataset version: " + datasetVersionId +
                        " of the workflow: " + datasetVersionId);
            }
        } catch (SQLException e) {
            // rollback the changes
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while setting details of dataset version " + datasetVersionId +
                    " of the model " + modelId + " to the database:" + e.getMessage(), e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement, result);
            MLDatabaseUtils.closeDatabaseResources(getDefaultFeatureSettings);
        }
    }

    /**
     * Retrieves the type of a feature.
     *
     * @param modelId     Unique identifier of the model
     * @param featureName Name of the feature
     * @return Type of the feature (Categorical/Numerical)
     * @throws DatabaseHandlerException
     */
    @Override
    public String getFeatureType(long modelId, String featureName) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement insertStatement = null;
        PreparedStatement getDefaultFeatureSettings = null;
        ResultSet result = null;
        try {
            MLDataSource dbh = new MLDataSource();
            connection = dbh.getDataSource().getConnection();
            getDefaultFeatureSettings = connection.prepareStatement(SQLQueries.GET_FEATURE_TYPE);
            getDefaultFeatureSettings.setLong(1, modelId);
            getDefaultFeatureSettings.setString(2, featureName);
            result = getDefaultFeatureSettings.executeQuery();
            if (result.first()) {
                return result.getString(1);
            } else {
                throw new DatabaseHandlerException("Invalid model Id: " + modelId);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while reading type of feature: "
                    + featureName + " of model Id: " + modelId + e.getMessage(), e);
        } finally {
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, insertStatement, result);
        }
    }

    /**
     * Change whether a feature should be included as an input or not.
     *
     * @param featureName Name of the feature to be updated
     * @param modelId     Unique identifier of the current model
     * @param isInput     Boolean value indicating whether the feature is an input or not
     * @throws DatabaseHandlerException
     */
    public void updateFeatureInclusion(String featureName, long modelId, boolean isInput)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            updateStatement = connection.prepareStatement(SQLQueries.UPDATE_FEATURE_INCLUSION);
            updateStatement.setBoolean(1, isInput);
            updateStatement.setString(2, featureName);
            updateStatement.setLong(3, modelId);
            updateStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the include-option of feature" + featureName +
                        "of model " + modelId);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while updating the feature included option of feature \"" +
                            featureName + "\" of model " + modelId + ": " + e, e);
        } finally {
            // Enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    /**
     * Update the impute method option of a given feature.
     *
     * @param featureName  Name of the feature to be updated
     * @param modelId      Unique identifier of the current model
     * @param imputeOption Updated impute option of the feature
     * @throws DatabaseHandlerException
     */
    public void updateImputeOption(String featureName, long modelId, String imputeOption)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            // Update the database.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            updateStatement = connection.prepareStatement(SQLQueries.UPDATE_IMPUTE_METHOD);
            updateStatement.setString(1, imputeOption);
            updateStatement.setString(2, featureName);
            updateStatement.setLong(3, modelId);
            updateStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the impute-option of feature" + featureName +
                        " of model " + modelId);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("An error occurred while updating the feature \"" +
                    featureName + "\" of model " + modelId + ": " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    /**
     * Update the data type of a given feature.
     *
     * @param featureName Name of the feature to be updated
     * @param modelId     Unique identifier of the current model
     * @param featureType Updated type of the feature
     * @throws DatabaseHandlerException
     */
    public void updateDataType(String featureName, long modelId, String featureType)
            throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement updateStatement = null;
        try {
            // Update the database with data type.
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            updateStatement = connection.prepareStatement(SQLQueries.UPDATE_DATA_TYPE);
            updateStatement.setString(1, featureType);
            updateStatement.setString(2, featureName);
            updateStatement.setLong(3, modelId);
            updateStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully updated the data-type of feature" + featureName +
                        " of model " + modelId);
            }
        } catch (SQLException e) {
            // Roll-back the changes.
            MLDatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException(
                    "An error occurred while updating the data type of feature \"" + featureName +
                            "\" of model " + modelId + ": " + e.getMessage(), e);
        } finally {
            // Enable auto commit.
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, updateStatement);
        }
    }

    // TODO
    @Override
    public void createNewWorkflow(String workflowID, String parentWorkflowID, String projectID, String datasetID, String workflowName) throws DatabaseHandlerException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public void createWorkflow(String workflowID, String projectID, String datasetID, String workflowName) throws DatabaseHandlerException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public void deleteWorkflow(String workflowID) throws DatabaseHandlerException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public String[][] getProjectWorkflows(String projectId) throws DatabaseHandlerException {
        return new String[0][];  //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public void updateWorkdflowName(String workflowId, String name) throws DatabaseHandlerException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public String getdatasetID(String projectId) throws DatabaseHandlerException {
        return "1234";  //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO
    @Override
    public String getDatasetId(String projectId) throws DatabaseHandlerException {
        return "1234";
    }

    // TODO
    public String getModelId(String workflowId) throws DatabaseHandlerException {

        Connection connection = null;
        PreparedStatement model = null;
        ResultSet result = null;
        try {
            connection = dbh.getDataSource().getConnection();
            model = connection.prepareStatement(SQLQueries.GET_MODEL_ID);
            model.setString(1, workflowId);
            result = model.executeQuery();
            if (!result.first()) {
            // need to query ML_MODEL table, just before model building process is started
            // to overcome building same model two (or more) times.
            // hence, null will be checked in UI.
                return null;
            }
            return result.getString(1);
        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    "An error occurred white retrieving model associated with workflow id " + workflowId +
                            ":" + e.getMessage(), e);
        } finally {
        // Close the database resources
            MLDatabaseUtils.closeDatabaseResources(connection, model, result);
        }
    }

    // TODO
    public Workflow getWorkflow(String workflowID) throws DatabaseHandlerException {
        return new Workflow();
    }

    // TODO
    public ModelSummary getModelSummary(String modelID) throws DatabaseHandlerException {
        Connection connection = null;
        ResultSet result = null;
        PreparedStatement getStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            getStatement = connection.prepareStatement(SQLQueries.GET_MODEL_SUMMARY);
            getStatement.setString(1, modelID);
            result = getStatement.executeQuery();
            if (result.first()) {
                return (ModelSummary) result.getObject(1);
            } else {
                throw new DatabaseHandlerException("Invalid model ID: " + modelID);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while reading model summary for " +
                    modelID + " from the database: " + e.getMessage(),
                    e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getStatement, result);
        }
    }

    // TODO
    public void insertModel(String modelID, String workflowID, Time executionStartTime)
            throws DatabaseHandlerException {

    }

    // TODO
    public void updateModel(String modelID, MLModel model,
                            ModelSummary modelSummary, Time executionEndTime)
            throws DatabaseHandlerException {

    }

    // TODO
    public MLModel getModel(String modelID) throws DatabaseHandlerException {
        Connection connection = null;
        ResultSet result = null;
        PreparedStatement getStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            getStatement = connection.prepareStatement(SQLQueries.GET_MODEL);
            getStatement.setString(1, modelID);
            result = getStatement.executeQuery();
            if (result.first()) {
                return (MLModel) result.getObject(1);
            } else {
                throw new DatabaseHandlerException("Invalid model ID: " + modelID);
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error occurred while reading model for " +
                    modelID + " from the database: " + e.getMessage(),
                    e);
        } finally {
            // enable auto commit
            MLDatabaseUtils.enableAutoCommit(connection);
            // Close the database resources.
            MLDatabaseUtils.closeDatabaseResources(connection, getStatement, result);
        }
    }

    // TODO
    public void insertModelSettings(String modelSettingsID, String workflowID, String
            algorithmName, String algorithmClass, String response, double trainDataFraction,
                                    List<HyperParameter> hyperparameters) throws DatabaseHandlerException {

    }

    // TODO
    public long getModelExecutionEndTime(String modelId) throws DatabaseHandlerException {
        return getModelExecutionTime(modelId, SQLQueries.GET_MODEL_EXE_END_TIME);
    }

    // TODO
    public long getModelExecutionStartTime(String modelId) throws DatabaseHandlerException {
        return getModelExecutionTime(modelId, SQLQueries.GET_MODEL_EXE_START_TIME);
    }

    // TODO
    /**
     * This helper class is used to extract model execution start/end time
     *
     * @param modelId
     * @param query
     * @return
     * @throws DatabaseHandlerException
     */
    public long getModelExecutionTime(String modelId, String query)
            throws DatabaseHandlerException {
        Connection connection = null;
        ResultSet result = null;
        PreparedStatement statement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, modelId);
            result = statement.executeQuery();
            if (result.first()) {
                Timestamp time = result.getTimestamp(1);
                if (time != null) {
                    return time.getTime();
                }
                return 0;
            } else {
                throw new DatabaseHandlerException(
                        "No timestamp data associated with model id: " + modelId);
            }

        } catch (SQLException e) {
            throw new DatabaseHandlerException(
                    " An error has occurred while reading execution time from the database: " + e
                            .getMessage(), e);
        } finally {
            // closing database resources
            MLDatabaseUtils.closeDatabaseResources(connection, statement, result);
        }
    }
}
