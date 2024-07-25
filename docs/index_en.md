---
title: Metadata Cleaning
identifier: intranda_step_kiel_archive_cleanup
published: true
description: Goobi Step Plugin for Manipulating and Cleaning Metadata for the City Archive Kiel
---
## Introduction
This documentation describes the installation, configuration and use of the Step Plugin for cleaning metadata for the Kiel City Archive. In concrete terms, this is about the automated evaluation of metadata that are to be separated into individual fields, such as information on the scales of historical maps. Furthermore, this plugin also copies the corresponding image files into the master folder of the respective process.


## Installation
The plugin consists of the following files to be installed:

```bash
plugin_intranda_step_kiel_archive_cleanup-base.jar
plugin_intranda_step_kiel_archive_cleanup.xml
```

The first file must be installed in the following directory:

```bash
/opt/digiverso/goobi/plugins/step/plugin_intranda_step_kiel_archive_cleanup-base.jar
```

In addition, there is a configuration file that must be located in the following place:

```bash
/opt/digiverso/goobi/plugins/config/plugin_intranda_step_kiel_archive_cleanup.xml
```


## Overview and functionality
The plugin is usually executed fully automatically within the workflow. It first determines whether there is a block in the configuration file that has been configured for the current workflow with regard to the project name and work step. If this is the case, the METS file is opened and the necessary changes to the METS file take place. Finally, the plugin determines those images that contain a prefix in the file name, which is also read from the METS file in order to copy them into the master folder of the workflow.  

This plugin is integrated into the workflow in such a way that it is executed automatically. Manual interaction with the plugin is not necessary. To use it within a step of the workflow, it should be configured as shown in the screenshot below.

![Integration of the plug-in into the workflow](screen1.png)


## Configuration
The configuration of the plugin is done via the configuration file `plugin_intranda_step_kiel_archive_cleanup.xml` and can be adjusted during operation. The following is an example configuration file:

```xml
<config_plugin>
    <!--
        order of configuration is:
          1.) project name and step name matches
          2.) step name matches and project is *
          3.) project name matches and step name is *
          4.) project name and step name are *
	-->

    <config>
        <!-- which projects to use for (can be more then one, otherwise use *) -->
        <project>*</project>
        <step>*</step>

        <!-- folder where to import images from -->
        <importFolder>/opt/digiverso/import/kiel/</importFolder>

        <!-- METS field which contains the map ID that can be used to automatically find the images for the process -->		
        <fieldForImagePrefix>UnitID</fieldForImagePrefix>

        <!-- Name of workflow steps which shall be deactivated if image files were found -->
        <stepToSkipIfImagesAvailable>Bilder einspielen</stepToSkipIfImagesAvailable>

        <!-- METS field that contains width, length and scale to be splitted into individual fields -->
        <size field="SizeSourcePrint"/>

        <!--  METS fields to create from splitted size field and terms to use for splitting the size field (used as "startsWith") -->
        <sizeWidth field="MapWidth" term="Breite"/>
        <sizeLength field="MapLength" term="Länge"/>
        <sizeScale field="MapScale" term="Maßstab"/>

    </config>

</config_plugin>
```

| Parameter | Explanation |
| :--- | :--- |
| `project` | This parameter determines for which project the current block `<config>` should apply. The name of the project is used here. This parameter can occur several times per `<config>` block. |
| `step` | This parameter controls for which workflow steps the block `<config>` should apply. The name of the workflow step is used here. This parameter can occur several times per `<config>` block. |
| `<importFolder>` | This parameter specifies the directory from which the images are to be copied. |
| `<fieldForImagePrefix>` | This parameter controls which metadata of the METS file is decisive for the selection of the images to be copied as prefix. |
| `<stepToSkipIfImagesAvailable>` | Here you can define how the workflow should behave in case of missing images. |
| `<size>` | Determination of the scale field to be evaluated. |
| `<sizeWidth>` | Definition of the field to be generated for the width |
| `<sizeLength>` | Determination of the field to be generated for the length |
| `<sizeScale>` | Determination of the field to be generated for the scale |
