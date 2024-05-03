package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.StepManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class KielArchiveCleanupStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_kiel_archive_cleanup";
    @Getter
    private Step step;
    private String size;
    private String sizeWidth;
    private String sizeLength;
    private String sizeScale;
    private String returnPath;
    private String importFolder;
    private String termWidth;
    private String termLength;
    private String termScale;
    private String creatorSource;
    private String creatorTarget;
    private List<Object> stepsToSkipIfImagesAvailable;
    private String fieldForImagePrefix;
    SubnodeConfiguration myconfig;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        // read parameters from correct block in configuration file
        myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        myconfig.setExpressionEngine(new XPathExpressionEngine());

        size = myconfig.getString("/size/@field");
        sizeWidth = myconfig.getString("/sizeWidth/@field");
        sizeLength = myconfig.getString("/sizeLength/@field");
        sizeScale = myconfig.getString("/sizeScale/@field");

        termWidth = myconfig.getString("/sizeWidth/@term");
        termLength = myconfig.getString("/sizeLength/@term");
        termScale = myconfig.getString("/sizeScale/@term");

        creatorSource = myconfig.getString("/creator/@source");
        creatorTarget = myconfig.getString("/creator/@target");

        stepsToSkipIfImagesAvailable = myconfig.getList("/stepToSkipIfImagesAvailable");
        importFolder = myconfig.getString("/importFolder");
        fieldForImagePrefix = myconfig.getString("/fieldForImagePrefix");

        //        size = myconfig.getString("size");
        //        sizeWidth = myconfig.getString("sizeWidth");
        //        sizeLength = myconfig.getString("sizeLength");
        //        sizeScale = myconfig.getString("sizeScale");
        //        termWidth = myconfig.getString("termWidth");
        //        termLength = myconfig.getString("termLength");
        //        termScale = myconfig.getString("termScale");
        //   		stepsToSkipIfImagesAvailable = myconfig.getList("stepToSkipIfImagesAvailable");
        //   		importFolder = myconfig.getString("importFolder");
        //   		fieldForImagePrefix = myconfig.getString("fieldForImagePrefix");
        log.info("KielArchiveCleanup step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_kiel_archive_cleanup.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = false;
        // your logic goes here

        try {
            // read mets file
            Fileformat ff = step.getProzess().readMetadataFile();
            Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
            DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
            DocStruct anchor = null;
            if (logical.getType().isAnchor()) {
                anchor = logical;
                logical = logical.getAllChildren().get(0);
            }

            // delete existing metadata of defined type
            if (logical.getAllMetadata() != null) {
                List<Metadata> originalMetadata = new ArrayList<>();
                for (Metadata md : logical.getAllMetadata()) {
                    if (md.getType().getName().equals(sizeWidth) || md.getType().getName().equals(sizeLength)
                            || md.getType().getName().equals(sizeScale)) {
                        originalMetadata.add(md);
                    }
                }
                for (Metadata metadata : originalMetadata) {
                    logical.removeMetadata(metadata);
                }
            }

            // find out size information from source field
            List<Metadata> metadatalist = new ArrayList<>();
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(size)) {
                    // split content into width, length and scale
                    String content = md.getValue();
                    //String[] words = content.split("\\s+");
                    String[] words = content.split("\\s{3,6}");
                    for (String s : words) {
                        if (StringUtils.isNotBlank(s)) {
                            if (s.startsWith(termWidth)) {
                                Metadata m = new Metadata(prefs.getMetadataTypeByName(sizeWidth));
                                m.setValue(getStringValueForField(s));
                                metadatalist.add(m);
                            }
                            if (s.startsWith(termLength)) {
                                Metadata m = new Metadata(prefs.getMetadataTypeByName(sizeLength));
                                m.setValue(getStringValueForField(s));
                                metadatalist.add(m);
                            }
                            if (s.startsWith(termScale)) {
                                Metadata m = new Metadata(prefs.getMetadataTypeByName(sizeScale));
                                m.setValue(getStringValueForField(s));
                                metadatalist.add(m);
                            }
                        }
                    }
                }
            }
            for (Metadata m : metadatalist) {
                logical.addMetadata(m);
            }

            // delete existing persons of defined type
            if (logical.getAllPersons() != null) {
                List<Person> originalPersons = new ArrayList<>();
                for (Person p : logical.getAllPersons()) {
                    if (p.getType().getName().equals(creatorTarget)) {
                        originalPersons.add(p);
                    }
                }
                for (Person p : originalPersons) {
                    logical.removePerson(p);
                }
            }

            // search for person information to move these into persons
            List<Person> persons = new ArrayList<>();
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(creatorSource)) {
                    Person p = new Person(prefs.getMetadataTypeByName(creatorTarget));
                    // get field content to split it
                    String lastname = md.getValue();
                    String identifier = "";
                    String firstname = "";
                    if (lastname.contains("|")) {
                        int i = lastname.indexOf("|");
                        identifier = lastname.substring(i + 1).trim();
                        lastname = lastname.substring(0, i);
                    }
                    if (lastname.contains(",")) {
                        int i = lastname.indexOf(",");
                        firstname = lastname.substring(i + 1).trim();
                        lastname = lastname.substring(0, i).trim();
                    }
                    p.setLastname(lastname);
                    p.setFirstname(firstname);
                    if (StringUtils.isNotBlank(identifier)) {
                        p.setAutorityFile("gnd", "http://d-nb.info/gnd/", identifier);
                    }
                    persons.add(p);
                }
            }
            for (Person p : persons) {
                logical.addPerson(p);
            }

            // save the mets file
            step.getProzess().writeMetadataFile(ff);

            // try to read unit id to find existing image files and copy these over
            String unitid = "";
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(fieldForImagePrefix)) {
                    unitid = md.getValue().trim();
                }
            }

            // if unit id is not blank, try to find images
            if (StringUtils.isNotBlank(unitid)) {
                boolean imagesFound = false;
                // get a prefix for image files
                String imagePrefix = getFileNameForUnitId(unitid);

                // if image prefix is not blank find matching files
                if (StringUtils.isNotBlank(imagePrefix)) {

                    // create master folder if not there
                    String targetBase = step.getProzess().getImagesOrigDirectory(false);
                    StorageProvider.getInstance().createDirectories(Paths.get(targetBase));

                    // run through all import files to find images that start with imageprefix in filename
                    Path input = Paths.get(importFolder);
                    List<Path> files = StorageProvider.getInstance().listFiles(input.toString(), kielFilter);
                    for (Path f : files) {
                        String filename = f.getFileName().toString();
                        if (filename.startsWith(imagePrefix)) {
                            StorageProvider.getInstance().copyFile(f, Paths.get(targetBase, filename));
                            imagesFound = true;
                        }

                    }
                }

                // if images were found and copied over then deactivate some workflow steps
                if (imagesFound) {
                    for (Object o : stepsToSkipIfImagesAvailable) {
                        String os = (String) o;
                        for (Step s : step.getProzess().getSchritteList()) {
                            if (s.getTitel().equals(os)) {
                                s.setBearbeitungsstatusEnum(StepStatus.DEACTIVATED);
                                StepManager.saveStep(s);
                            }
                        }
                    }
                }
            }

            successful = true;
        } catch (ReadException | PreferencesException | WriteException | IOException | SwapException | DAOException
                | MetadataTypeNotAllowedException e) {
            log.error(e);
        }

        log.info("KielArchiveCleanup step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    /**
     * Get the content of a string after the first colon and trim it
     * 
     * @param inContent
     * @return
     */
    private String getStringValueForField(String inContent) {
        if (inContent.contains(":")) {
            return inContent.substring(inContent.indexOf(":") + 1).trim();
        }
        return inContent;
    }

    /**
     * Generate a 5-digit prefix for image filenames out of unit id
     * 
     * @param unitId
     * @return
     */
    private String getFileNameForUnitId(String unitId) {
        String[] sn = unitId.split("\\s+");
        String result = "";
        if (sn.length > 1) {
            int number;
            try {
                number = Integer.parseInt(sn[1].trim());
                result = sn[0].trim();
                result += String.format("%05d", number);
            } catch (NumberFormatException e) {
            }
        }
        return result;
    }

    /**
     * File filter for image files
     */
    public static final DirectoryStream.Filter<Path> kielFilter = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path path) {
            String n = path.getFileName().toString();
            return n.endsWith(".jpeg") || n.endsWith(".jpg") || n.endsWith(".tif") || n.endsWith(".tiff");
        }
    };
}
