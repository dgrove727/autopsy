/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Handles locating and opening multi-user cases.
 */
final class MultiUserCaseManager {

    private static final Logger LOGGER = Logger.getLogger(MultiUserCaseManager.class.getName());
    private static MultiUserCaseManager instance;
    
    private CoordinationService coordinationService;

    /**
     * Gets the multi-user case manager.
     *
     * @return The multi-user case manager singleton.
     */
    synchronized static MultiUserCaseManager getInstance() throws MultiUserCaseManager.MultiUserCaseManagerException {
        if (null == instance) {
            instance = new MultiUserCaseManager();
        }
        return instance;
    }

    /**
     * Constructs an object that handles locating and opening multi-user cases.
     */
    private MultiUserCaseManager() throws MultiUserCaseManagerException {
        try {
            init();
        } catch (MultiUserCaseManager.MultiUserCaseManagerException ex) {
            Logger.getLogger(MultiUserCaseManager.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }
    
    /**
     * Initialize the coordination service.
     * 
     * @throws org.sleuthkit.autopsy.casemodule.MultiUserCaseManager.MultiUserCaseManagerException 
     */
    private void init() throws MultiUserCaseManager.MultiUserCaseManagerException {
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new MultiUserCaseManager.MultiUserCaseManagerException(ex.getMessage(), ex);
        }
    }

    /**
     * Gets a list of the cases in the top level case folder
     */
    @Messages({
        "MultiUserCaseManager.CasePathsError=An error occurred while trying to retrieve the list of case paths."
    })
    List<MultiUserCase> getCases() {
        List<MultiUserCase> cases = new ArrayList<>();
        try {
            List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
            for (String node : nodeList) {
                Path casePath = Paths.get(node);
                File caseFolder = casePath.toFile();
                if(caseFolder.exists()) {
                    File[] autFiles = caseFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".aut"));
                    if(autFiles != null && autFiles.length > 0) {
                        cases.add(new MultiUserCase(casePath));
                    }
                }
            }
        } catch (CoordinationServiceException ex) {
            String errorMessage = NbBundle.getMessage(MultiUserCaseManager.class, "MultiUserCaseManager.CasePathsError");
            LOGGER.log(Level.SEVERE, errorMessage, ex);
            MessageNotifyUtil.Message.error(errorMessage);
        } 
        return cases;
    }

    /**
     * Opens a multi-user case.
     *
     * @param caseMetadataFilePath Path to the case metadata file.
     *
     * @throws CaseActionException
     */
    synchronized void openCase(Path caseMetadataFilePath) throws CaseActionException {
        /*
         * Open the case.
         */
        Case.openAsCurrentCase(caseMetadataFilePath.toString());
    }
    
    /**
     * Exception type thrown when there is an error completing a multi-user case
     * manager operation.
     */
    static final class MultiUserCaseManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing a multi-user case manager operation.
         *
         * @param message The exception message.
         */
        private MultiUserCaseManagerException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing a multi-user case manager operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private MultiUserCaseManagerException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
