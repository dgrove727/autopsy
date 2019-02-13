/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.thunderbirdparser;

import ezvcard.VCard;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Email;
import ezvcard.property.Organization;
import ezvcard.property.Telephone;
import ezvcard.property.Url;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Relationship;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/**
 * File-level ingest module that detects MBOX, PST, and vCard files based on
 * signature. Understands Thunderbird folder layout to provide additional
 * structure and metadata.
 */
public final class ThunderbirdMboxFileIngestModule implements FileIngestModule {
    private static final String VCARD_TEL_TYPE_HOME = "home";
    private static final String VCARD_TEL_TYPE_WORK = "work";
    private static final String VCARD_TEL_TYPE_TEXT = "text";
    private static final String VCARD_TEL_TYPE_VOICE = "voice";
    private static final String VCARD_TEL_TYPE_FAX = "fax";
    private static final String VCARD_TEL_TYPE_CELL = "cell";
    private static final String VCARD_TEL_TYPE_VIDEO = "video";
    private static final String VCARD_TEL_TYPE_PAGER = "pager";
    private static final String VCARD_TEL_TYPE_TEXTPHONE = "textphone";
    private static final String VCARD_TEL_TYPE_MAIN_NUMBER = "main-number";
    private static final String VCARD_TEL_TYPE_MSG = "msg";
    private static final String VCARD_TEL_TYPE_PREF = "pref";
    private static final String VCARD_TEL_TYPE_BBS = "bbs";
    private static final String VCARD_TEL_TYPE_MODEM = "modem";
    private static final String VCARD_TEL_TYPE_CAR = "car";
    private static final String VCARD_TEL_TYPE_ISDN = "isdn";
    private static final String VCARD_TEL_TYPE_PCS = "pcs";
    
    private static final String VCARD_EMAIL_TYPE_HOME = "home";
    private static final String VCARD_EMAIL_TYPE_WORK = "work";
    private static final String VCARD_EMAIL_TYPE_INTERNET = "internet";
    private static final String VCARD_EMAIL_TYPE_X400 = "x400";
    private static final String VCARD_EMAIL_TYPE_PREF = "pref";
    
    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private IngestServices services = IngestServices.getInstance();
    private FileManager fileManager;
    private IngestJobContext context;
    private Blackboard blackboard;

    ThunderbirdMboxFileIngestModule() {
    }

    @Override
    @Messages ({"ThunderbirdMboxFileIngestModule.noOpenCase.errMsg=Exception while getting open case."})
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        try {
            fileManager = Case.getCurrentCaseThrows().getServices().getFileManager();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            throw new IngestModuleException(Bundle.ThunderbirdMboxFileIngestModule_noOpenCase_errMsg(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return ProcessResult.ERROR;
        }

        // skip known
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        //skip unalloc
        if ((abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) ||
                (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return ProcessResult.OK;
        }

        if ((abstractFile.isFile() == false)) {
            return ProcessResult.OK;
        }

        // check its signature
        boolean isMbox = false;
        try {
            byte[] t = new byte[64];
            if (abstractFile.getSize() > 64) {
                int byteRead = abstractFile.read(t, 0, 64);
                if (byteRead > 0) {
                    isMbox = MboxParser.isValidMimeTypeMbox(t);
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, null, ex);
        }

        if (isMbox) {
            return processMBox(abstractFile);
        }

        if (PstParser.isPstFile(abstractFile)) {
            return processPst(abstractFile);
        }
        
        if (VcardParser.isVcardFile(abstractFile)) {
            return processVcard(abstractFile);
        }

        return ProcessResult.OK;
    }

    /**
     * Processes a pst/ost data file and extracts and adds email artifacts.
     *
     * @param abstractFile The pst/ost data file to process.
     *
     * @return
     */
    @Messages({"ThunderbirdMboxFileIngestModule.processPst.indexError.message=Failed to index encryption detected artifact for keyword search."})
    private ProcessResult processPst(AbstractFile abstractFile) {
        String fileName;
        try {
            fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        File file = new File(fileName);

        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk."); //NON-NLS
            IngestMessage msg = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "ThunderbirdMboxFileIngestModule.processPst.errMsg.outOfDiskSpace",
                            abstractFile.getName()));
            services.postMessage(msg);
            return ProcessResult.OK;
        }

        try {
            ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing pst file to disk.", ex); //NON-NLS
            return ProcessResult.OK;
        }

        PstParser parser = new PstParser(services);
        PstParser.ParseResult result = parser.parse(file, abstractFile.getId());

        if (result == PstParser.ParseResult.OK) {
            try {
                // parse success: Process email and add artifacts
                processEmails(parser.getResults(), abstractFile);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
                return ProcessResult.ERROR;
            }

        } else if (result == PstParser.ParseResult.ENCRYPT) {
            // encrypted pst: Add encrypted file artifact
            try {
                BlackboardArtifact artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);
                artifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, EmailParserModuleFactory.getModuleName(), NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.encryptionFileLevel")));

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(artifact);
                } catch (Blackboard.BlackboardException ex) {
                    MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_processPst_indexError_message(), artifact.getDisplayName());
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                }

                services.fireModuleDataEvent(new ModuleDataEvent(EmailParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED));
            } catch (TskCoreException ex) {
                logger.log(Level.INFO, "Failed to add encryption attribute to file: {0}", abstractFile.getName()); //NON-NLS
            }
        } else {
            // parsing error: log message
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg",
                            abstractFile.getName()),
                    NbBundle.getMessage(this.getClass(),
                            "ThunderbirdMboxFileIngestModule.processPst.errProcFile.details"));
            logger.log(Level.INFO, "PSTParser failed to parse {0}", abstractFile.getName()); //NON-NLS
            return ProcessResult.ERROR;
        }

        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
        }

        String errors = parser.getErrors();
        if (errors.isEmpty() == false) {
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg2",
                            abstractFile.getName()), errors);
        }

        return ProcessResult.OK;
    }

    /**
     * Parse and extract email messages and attachments from an MBox file.
     *
     * @param abstractFile
     *
     * @return
     */
    private ProcessResult processMBox(AbstractFile abstractFile) {
        String mboxFileName = abstractFile.getName();
        String mboxParentDir = abstractFile.getParentPath();
        // use the local path to determine the e-mail folder structure
        String emailFolder = "";
        // email folder is everything after "Mail" or ImapMail
        if (mboxParentDir.contains("/Mail/")) { //NON-NLS
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/Mail/") + 5); //NON-NLS
        } else if (mboxParentDir.contains("/ImapMail/")) { //NON-NLS
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/ImapMail/") + 9); //NON-NLS
        }
        emailFolder = emailFolder + mboxFileName;
        emailFolder = emailFolder.replaceAll(".sbd", ""); //NON-NLS

        String fileName;
        try {
            fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        File file = new File(fileName);

        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk."); //NON-NLS
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg",
                            abstractFile.getName()),
                    NbBundle.getMessage(this.getClass(),
                            "ThunderbirdMboxFileIngestModule.processMBox.errProfFile.details"));
            return ProcessResult.OK;
        }

        try {
            ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing mbox file to disk.", ex); //NON-NLS
            return ProcessResult.OK;
        }

        MboxParser parser = new MboxParser(services, emailFolder);
        List<EmailMessage> emails = parser.parse(file, abstractFile.getId());
        try {
            processEmails(emails, abstractFile);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
        }

        String errors = parser.getErrors();
        if (errors.isEmpty() == false) {
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg2",
                            abstractFile.getName()), errors);
        }

        return ProcessResult.OK;
    }
    
    /**
     * Parse and extract data from a vCard file.
     *
     * @param abstractFile The content to be processed.
     *
     * @return 'ERROR' whenever a NoCurrentCaseException is encountered;
     *         otherwise 'OK'.
     */
    @Messages({
        "# {0} - file name",
        "# {1} - file ID",
        "ThunderbirdMboxFileIngestModule.errorMessage.outOfDiskSpace=Out of disk space. Cannot copy '{0}' (id={1}) to parse."
    })
    private ProcessResult processVcard(AbstractFile abstractFile) {
        String fileName;
        try {
            fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        File file = new File(fileName);

        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, String.format("Not enough disk space to write file '%s' (id=%d) to disk.",
                    abstractFile.getName(), abstractFile.getId())); //NON-NLS
            IngestMessage msg = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleName(),
                    Bundle.ThunderbirdMboxFileIngestModule_errorMessage_outOfDiskSpace(abstractFile.getName(), abstractFile.getId()));
            services.postMessage(msg);
            return ProcessResult.OK;
        }

        try {
            ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Failed writing the vCard file '%s' (id=%d) to disk.",
                    abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
            return ProcessResult.OK;
        }
        
        VcardParser parser = new VcardParser();
        VCard vcard;
        
        try {
            vcard = parser.parse(file);
            addContactArtifact(vcard, abstractFile);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Exception while parsing the file '%s' (id=%d).", file.getName(), abstractFile.getId()), ex); //NON-NLS
            return ProcessResult.OK;
        }
        
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
        }
        
        return ProcessResult.OK;
    }

    /**
     * Get a path to a temporary folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the temporary folder
     */
    static String getTempPath() throws NoCurrentCaseException {
        String tmpDir = Case.getCurrentCaseThrows().getTempDirectory() + File.separator
                + "EmailParser"; //NON-NLS
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }

    /**
     * Get a module output folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the module output folder
     */
    static String getModuleOutputPath() throws NoCurrentCaseException {
        String outDir = Case.getCurrentCaseThrows().getModuleDirectory() + File.separator
                + EmailParserModuleFactory.getModuleName();
        File dir = new File(outDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return outDir;
    }

    /**
     * Get a relative path of a module output folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the relative path of the module output folder
     */
    static String getRelModuleOutputPath() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getModuleOutputDirectoryRelativePath() + File.separator
                + EmailParserModuleFactory.getModuleName();
    }

    /**
     * Take the extracted information in the email messages and add the
     * appropriate artifacts and derived files.
     *
     * @param emails
     * @param abstractFile
     * @throws NoCurrentCaseException if there is no open case.
     */
    private void processEmails(List<EmailMessage> emails, AbstractFile abstractFile) throws NoCurrentCaseException {
        List<AbstractFile> derivedFiles = new ArrayList<>();
        
       
        
        for (EmailMessage email : emails) {
            BlackboardArtifact msgArtifact = addEmailArtifact(email, abstractFile);
             
            if ((msgArtifact != null) && (email.hasAttachment()))  {
                derivedFiles.addAll(handleAttachments(email.getAttachments(), abstractFile, msgArtifact ));
            }
        }

        if (derivedFiles.isEmpty() == false) {
            for (AbstractFile derived : derivedFiles) {
                services.fireModuleContentEvent(new ModuleContentEvent(derived));
            }
        }
        context.addFilesToJob(derivedFiles);
        services.fireModuleDataEvent(new ModuleDataEvent(EmailParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
    }

    /**
     * Add the given attachments as derived files and reschedule them for
     * ingest.
     *
     * @param attachments
     * @param abstractFile
     * @param messageArtifact
     *
     * @return List of attachments
     */
    private List<AbstractFile> handleAttachments(List<EmailMessage.Attachment> attachments, AbstractFile abstractFile, BlackboardArtifact messageArtifact) {
        List<AbstractFile> files = new ArrayList<>();
        for (EmailMessage.Attachment attach : attachments) {
            String filename = attach.getName();
            long crTime = attach.getCrTime();
            long mTime = attach.getmTime();
            long aTime = attach.getaTime();
            long cTime = attach.getcTime();
            String relPath = attach.getLocalPath();
            long size = attach.getSize();
            TskData.EncodingType encodingType = attach.getEncodingType();

            try {
                DerivedFile df = fileManager.addDerivedFile(filename, relPath,
                        size, cTime, crTime, aTime, mTime, true, messageArtifact, "",
                        EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleVersion(), "", encodingType);
                files.add(df);
            } catch (TskCoreException ex) {
                postErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.handleAttch.errMsg",
                                abstractFile.getName()),
                        NbBundle.getMessage(this.getClass(),
                                "ThunderbirdMboxFileIngestModule.handleAttch.errMsg.details", filename));
                logger.log(Level.INFO, "", ex);
            }
        }
        return files;
    }

    /**
     * Finds and returns a set of unique email addresses found in the input string
     *
     * @param input - input string, like the To/CC line from an email header
     * 
     * @return Set<String>: set of email addresses found in the input string
     */
    private Set<String> findEmailAddresess(String input) {
        Pattern p = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
                                    Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(input);
        Set<String> emailAddresses = new HashSet<String>();
        while (m.find()) {
            emailAddresses.add( m.group());
        }
        return emailAddresses;
    }
    
    /**
     * Add a blackboard artifact for the given e-mail message.
     *
     * @param email The e-mail message.
     * @param abstractFile The associated file.
     * 
     * @return The generated e-mail message artifact.
     * 
     * @throws NoCurrentCaseException If there is no open case.
     */
    @Messages({"ThunderbirdMboxFileIngestModule.addArtifact.indexError.message=Failed to index email message detected artifact for keyword search."})
    private BlackboardArtifact addEmailArtifact(EmailMessage email, AbstractFile abstractFile) throws NoCurrentCaseException {
        BlackboardArtifact bbart = null;
        List<BlackboardAttribute> bbattributes = new ArrayList<>();
        String to = email.getRecipients();
        String cc = email.getCc();
        String bcc = email.getBcc();
        String from = email.getSender();
        long dateL = email.getSentDate();
        String headers = email.getHeaders();
        String body = email.getTextBody();
        String bodyHTML = email.getHtmlBody();
        String rtf = email.getRtfBody();
        String subject = email.getSubject();
        long id = email.getId();
        String localPath = email.getLocalPath();

        List<String> senderAddressList = new ArrayList<>();
        String senderAddress;
        senderAddressList.addAll(findEmailAddresess(from));
        
        AccountFileInstance senderAccountInstance = null;

        Case openCase = Case.getCurrentCaseThrows();
        
        if (senderAddressList.size() == 1) {
            senderAddress = senderAddressList.get(0);
            try {
                senderAccountInstance = openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, senderAddress, EmailParserModuleFactory.getModuleName(), abstractFile);
            }
            catch(TskCoreException ex) {
                 logger.log(Level.WARNING, "Failed to create account for email address  " + senderAddress, ex); //NON-NLS
            }
        }
        else {
             logger.log(Level.WARNING, "Failed to find sender address, from  = "+ from); //NON-NLS
        }
        
        List<String> recipientAddresses = new ArrayList<>();
        recipientAddresses.addAll(findEmailAddresess(to));
        recipientAddresses.addAll(findEmailAddresess(cc));
        recipientAddresses.addAll(findEmailAddresess(bcc));
        
        List<AccountFileInstance> recipientAccountInstances = new ArrayList<>();
        recipientAddresses.forEach((addr) -> {
            try {
                AccountFileInstance recipientAccountInstance = 
                openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, addr,
                        EmailParserModuleFactory.getModuleName(), abstractFile);
                recipientAccountInstances.add(recipientAccountInstance);
            }
            catch(TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create account for email address  " + addr, ex); //NON-NLS
            }
        });
                
        addArtifactAttribute(headers, ATTRIBUTE_TYPE.TSK_HEADERS, bbattributes);
        addArtifactAttribute(from, ATTRIBUTE_TYPE.TSK_EMAIL_FROM, bbattributes);
        addArtifactAttribute(to, ATTRIBUTE_TYPE.TSK_EMAIL_TO, bbattributes);
        addArtifactAttribute(subject, ATTRIBUTE_TYPE.TSK_SUBJECT, bbattributes);
        
        addArtifactAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_RCVD, bbattributes);
        addArtifactAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_SENT, bbattributes);
        
        addArtifactAttribute(body, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN, bbattributes);
        
        addArtifactAttribute(((id < 0L) ? NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.notAvail") : String.valueOf(id)), 
                ATTRIBUTE_TYPE.TSK_MSG_ID, bbattributes);
        
        addArtifactAttribute(((localPath.isEmpty() == false) ? localPath : "/foo/bar"), 
                ATTRIBUTE_TYPE.TSK_PATH, bbattributes);
        
        addArtifactAttribute(cc, ATTRIBUTE_TYPE.TSK_EMAIL_CC, bbattributes);
        addArtifactAttribute(bodyHTML, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML, bbattributes);
        addArtifactAttribute(rtf, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF, bbattributes);
        
   
        try {
            
            bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
            bbart.addAttributes(bbattributes);

            // Add account relationships
            openCase.getSleuthkitCase().getCommunicationsManager().addRelationships(senderAccountInstance, recipientAccountInstances, bbart,Relationship.Type.MESSAGE, dateL);
            
            try {
                // index the artifact for keyword search
                blackboard.indexArtifact(bbart);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bbart.getArtifactID(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_addArtifact_indexError_message(), bbart.getDisplayName());
            }
        } catch (TskCoreException | TskDataException ex) {
            logger.log(Level.WARNING, null, ex);
        }

        return bbart;
    }
    
    /**
     * Add a blackboard artifact for the given contact.
     *
     * @param vcard The vCard that contains the contact information.
     * @param abstractFile The file associated with the data.
     * 
     * @return The generated contact artifact.
     * 
     * @throws NoCurrentCaseException If there is no open case.
     */
    @Messages({"ThunderbirdMboxFileIngestModule.addContactArtifact.indexError=Failed to index the contact artifact for keyword search."})
    private BlackboardArtifact addContactArtifact(VCard vcard, AbstractFile abstractFile) throws NoCurrentCaseException {
        Case currentCase = Case.getCurrentCaseThrows();
        SleuthkitCase tskCase = currentCase.getSleuthkitCase();
        
        List<BlackboardAttribute> attributes = new ArrayList<>();
        List<AccountFileInstance> accountInstances = new ArrayList<>();
        
        addArtifactAttribute(vcard.getFormattedName().getValue(), ATTRIBUTE_TYPE.TSK_NAME_PERSON, attributes);
        
        for (Telephone telephone : vcard.getTelephoneNumbers()) {
            String telephoneText = telephone.getText();
            if (telephoneText == null || telephoneText.isEmpty()) {
                continue;
            }
            
            // Add phone number to collection for later creation of TSK_CONTACT.
            List<TelephoneType> telephoneTypes = telephone.getTypes();
            if (telephoneTypes.isEmpty()) {
                addArtifactAttribute(telephone.getText(), ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, attributes);
            } else {
                for (TelephoneType type : telephoneTypes) {
                    BlackboardAttribute.ATTRIBUTE_TYPE attributeType;

                    switch (type.getValue().toLowerCase()) {
                        case VCARD_TEL_TYPE_HOME:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME;
                            break;
                        case VCARD_TEL_TYPE_WORK:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE;
                            break;
                        case VCARD_TEL_TYPE_TEXT:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TEXT;
                            break;
                        case VCARD_TEL_TYPE_FAX:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FAX;
                            break;
                        case VCARD_TEL_TYPE_CELL:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE;
                            break;
                        case VCARD_TEL_TYPE_VIDEO:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_VIDEO;
                            break;
                        case VCARD_TEL_TYPE_PAGER:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_PAGER;
                            break;
                        case VCARD_TEL_TYPE_TEXTPHONE:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TEXTPHONE;
                            break;
                        case VCARD_TEL_TYPE_MSG:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_VOICE_MESSAGING;
                            break;
                        case VCARD_TEL_TYPE_BBS:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_BBS;
                            break;
                        case VCARD_TEL_TYPE_MODEM:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MODEM;
                            break;
                        case VCARD_TEL_TYPE_CAR:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_CAR;
                            break;
                        case VCARD_TEL_TYPE_ISDN:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_ISDN;
                            break;
                        case VCARD_TEL_TYPE_PCS:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_PCS;
                            break;
                        case VCARD_TEL_TYPE_MAIN_NUMBER:
                        case VCARD_TEL_TYPE_PREF:
                        case VCARD_TEL_TYPE_VOICE:
                            // Fall-thru
                        default:
                            attributeType = ATTRIBUTE_TYPE.TSK_PHONE_NUMBER;
                            break;
                    }

                    addArtifactAttribute(telephone.getText(), attributeType, attributes);
                }
            }
            
            // Add phone number as a TSK_ACCOUNT.
            try {
                AccountFileInstance phoneAccountInstance = tskCase.getCommunicationsManager().createAccountFileInstance(Account.Type.PHONE,
                        telephoneText, EmailParserModuleFactory.getModuleName(), abstractFile);
                accountInstances.add(phoneAccountInstance);
            }
            catch(TskCoreException ex) {
                 logger.log(Level.WARNING, String.format(
                         "Failed to create account for phone number '%s' (content='%s'; id=%d).",
                         telephoneText, abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
            }
        }
        
        for (Email email : vcard.getEmails()) {
            String emailValue = email.getValue();
            if (emailValue == null || emailValue.isEmpty()) {
                continue;
            }
            
            // Add phone number to collection for later creation of TSK_CONTACT.
            List<EmailType> emailTypes = email.getTypes();
            if (emailTypes.isEmpty()) {
                addArtifactAttribute(email.getValue(), ATTRIBUTE_TYPE.TSK_EMAIL, attributes);
            } else {
                for (EmailType type : email.getTypes()) {
                    BlackboardAttribute.ATTRIBUTE_TYPE attributeType;

                    switch (type.getValue().toLowerCase()) {
                        case VCARD_EMAIL_TYPE_HOME:
                            attributeType = ATTRIBUTE_TYPE.TSK_EMAIL_HOME;
                            break;
                        case VCARD_EMAIL_TYPE_WORK:
                            attributeType = ATTRIBUTE_TYPE.TSK_EMAIL_OFFICE;
                            break;
                        case VCARD_EMAIL_TYPE_X400:
                            attributeType = ATTRIBUTE_TYPE.TSK_EMAIL_X400;
                            break;
                        case VCARD_EMAIL_TYPE_INTERNET:
                            // Fall-thru
                        default:
                            attributeType = ATTRIBUTE_TYPE.TSK_EMAIL;
                            break;
                    }

                    addArtifactAttribute(email.getValue(), attributeType, attributes);
                }
            }
            
            // Add phone number as a TSK_ACCOUNT.
            try {
                AccountFileInstance emailAccountInstance = tskCase.getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL,
                        emailValue, EmailParserModuleFactory.getModuleName(), abstractFile);
                accountInstances.add(emailAccountInstance);
            }
            catch(TskCoreException ex) {
                 logger.log(Level.WARNING, String.format(
                         "Failed to create account for e-mail address '%s' (content='%s'; id=%d).",
                         emailValue, abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
            }
        }
        
        for (Url url : vcard.getUrls()) {
            addArtifactAttribute(url.getValue(), ATTRIBUTE_TYPE.TSK_URL, attributes);
        }
        
        for (Organization organization : vcard.getOrganizations()) {
            List<String> values = organization.getValues();
            if (values.isEmpty() == false) {
                addArtifactAttribute(values.get(0), ATTRIBUTE_TYPE.TSK_ORGANIZATION, attributes);
            }
        }
        
        // Add 'DEVICE' TSK_ACCOUNT.
        AccountFileInstance deviceAccountInstance = null;
        String deviceId = null;
        try {
            long dataSourceObjId = abstractFile.getDataSourceObjectId();
            DataSource dataSource = tskCase.getDataSource(dataSourceObjId);
            deviceId = dataSource.getDeviceId();
            deviceAccountInstance = tskCase.getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE,
                    deviceId, EmailParserModuleFactory.getModuleName(), abstractFile);
        }
        catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format(
                    "Failed to create device account for '%s' (content='%s'; id=%d).",
                    deviceId, abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
        }
        catch (TskDataException ex) {
            logger.log(Level.WARNING, String.format(
                    "Failed to get the data source from the case database (id=%d).",
                    abstractFile.getId()), ex); //NON-NLS
        }
   
        BlackboardArtifact artifact = null;
        org.sleuthkit.datamodel.Blackboard tskBlackboard = tskCase.getBlackboard();
        try {
            // Create artifact if it doesn't already exist.
            if (!tskBlackboard.artifactExists(abstractFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT, attributes)) {
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                artifact.addAttributes(attributes);
                List<BlackboardArtifact> blackboardArtifacts = new ArrayList<>();
                blackboardArtifacts.add(artifact);
                
                // Add account relationships.
                if (deviceAccountInstance != null) {
                    try {
                        currentCase.getSleuthkitCase().getCommunicationsManager().addRelationships(
                                deviceAccountInstance, accountInstances, artifact, Relationship.Type.CONTACT, abstractFile.getCrtime());
                    } catch (TskDataException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to create phone and e-mail account relationships (fileName='%s'; fileId=%d; accountId=%d).",
                                abstractFile.getName(), abstractFile.getId(), deviceAccountInstance.getAccount().getAccountID()), ex); //NON-NLS
                    }
                }
                
                // Index the artifact for keyword search.
                try {
                    blackboard.indexArtifact(artifact);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_addContactArtifact_indexError(), artifact.getDisplayName());
                }
                
                // Fire event to notify UI of this new artifact.
                IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                        EmailParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT,
                        blackboardArtifacts));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to create contact artifact for vCard file '%s' (id=%d).",
                    abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
            logger.log(Level.WARNING, String.format(
                    "Failed to get the data source from the case database (id=%d).",
                    abstractFile.getId()), ex); //NON-NLS
        }

        return artifact;
    }

    private void addArtifactAttribute(String stringVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (stringVal.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), stringVal));
        }
    }
    private void addArtifactAttribute(long longVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (longVal > 0) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), longVal));
        }
    }
    
    void postErrorMessage(String subj, String details) {
        IngestMessage ingestMessage = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleVersion(), subj, details);
        services.postMessage(ingestMessage);
    }

    IngestServices getServices() {
        return services;
    }

    @Override
    public void shutDown() {
        // nothing to shut down
    }
}
