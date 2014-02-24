# Overview

## Introduction

In Force.com development, developers have 2 options:
1. use the web-based development tools
2. use the Force.com IDE and sync with the online deployment environment

Even with option 2, there are some things that are difficult to do (such as static resources) and complications when
working with a large team.

These are usually not a problem, but if developing large, custom components (such as a Single Page App that lives
within Force.com), this can become a challenge.

The webtosf-maven-plugin was developed to address these challenges. It allows developers to write their apps outside
of the Force.com world provided they have minimal dependence on being within Force.com containers. If you are working
 mainly with HTML/CSS and relying on the Force.com REST APIs, possibly only using container information at the top
 level, you can now build that app out as you normally would (HTML, CSS, deploy to Jetty or Tomcat). Then configure
 the plugin to create the staticresources and .page files needed to copy the application to Force.com for deployment.

## Distribution

Repository: TBD
GroupId: com.summa
ArtifactId: webtosf-maven-plugin</artifactId>
Version: 0.2

```xml
<plugin>
    <groupId>com.summa</groupId>
    <artifactId>webtosf-maven-plugin</artifactId>
    <version>0.2</version>
    ...
</plugin>
```

## Goals

### Goals available for this plugin

| Goal | Description |
| ------------- | ------------- |
| webtosf | Convert standard web project to force.com project |

### webtosf

**Full name:**
com.summa:webtosf-maven-plugin:0.2:webtosf-maven-plugin

**Description:**
Converts a standard web project to a Force.com project

**Parameters:**

| Name      | Type    | Required | Description                              |
| --------- | ------- | -------- | ---------------------------------------- |
| webappDir | FileSet | true     | Files to pull into the Force.com project |
| outputDir | File | false | Path for the generated Force.com files. Default: ${project.build.directory}/${project.build.finalName} |
| filters | List<Filter> | false | Filters to be applied to each html file. Each filter contains a token to search for, a value to replace and an isRegex field  |
| zipFileName | String| false | Name for the zip of resources. Default is appzip |

### System requirements

| Maven | 3.0 |
| JDK | 1.6 |
| Memory | No minimum |
| Disk space | No minimum |

## Usage: webtosf

### Without pom.xml change

To convert to Force.com project:

```
$> mvn com.summa:webtosf-maven-plugin:0.2:webtosf-maven-plugin
```

Note: I haven't actually tested this

### With pom.xml change

```xml
<project>
  ...
  <build>
    <plugins>
      ...
      <plugin>
        <groupId>com.summa</groupId>
        <artifactId>webtosf-maven-plugin</artifactId>
        <version>0.2</version>
          <executions>
              <execution>
              <phase>package</phase>
              <goals>
                  <goal>webtosf</goal>
              </goals>
              <configuration>
                  <webappDir>
                      <directory>${webapp.dir}</directory>
                      <includes>
                          <include>css/**</include>
                          <include>js/**</include>
                          <include>MyApp.html</include>
                      </includes>
                  </webappDir>
              </configuration>
              </execution>
          </executions>
        </plugin>
        ...
    </plugins>
  </build>
  ...
</project>
```

# Examples

## Custom filters

Most of the replacements you need to convert HTML to .page files are already in place by the plugin. If you need
additional filters, you can add them in a <filters> tag. For example, using Angular,
you may need to copy some additional html files that will get flattened by the plugin. Here's how you would rename
references to them:

```xml
<project>
...
  <build>
    <plugins>
      ...
      <plugin>
        <groupId>com.summa</groupId>
        <artifactId>webtosf-maven-plugin</artifactId>
        <version>0.2</version>
          <executions>
              <execution>
              <phase>package</phase>
              <goals>
                  <goal>webtosf</goal>
              </goals>
              <configuration>
                  <webappDir>
                      <directory>${webapp.dir}</directory>
                      <includes>
                          <include>css/**</include>
                          <include>js/**</include>
                          <include>MyApp.html</include>
                      </includes>
                  </webappDir>
                  <filters>
                      <filter>
                          <token>partials/detail.html</token>
                          <value>partialsdetail</value>
                      </filter>
                      <filter>
                          <token>partials/list.html</token>
                          <value>partialslist</value>
                      </filter>
                  </filters>
              </configuration>
              </execution>
          </executions>
        </plugin>
        ...
    </plugins>
  </build>
  ...
</project>
```

## Regex Filters

You may also find it useful to build regex filters using Java regex notation. Here is how that will look. **Note**:
the filter below is built in.

```xml
<project>
...
  <build>
    <plugins>
      ...
      <plugin>
        <groupId>com.summa</groupId>
        <artifactId>webtosf-maven-plugin</artifactId>
        <version>0.2</version>
          <executions>
              <execution>
              <phase>package</phase>
              <goals>
                  <goal>webtosf</goal>
              </goals>
              <configuration>
                  <webappDir>
                      <directory>${webapp.dir}</directory>
                      <includes>
                          <include>css/**</include>
                          <include>js/**</include>
                          <include>MyApp.html</include>
                      </includes>
                  </webappDir>
                  <filters>
                      <filter>
                          <token>(.*)<script src=["'](.+)["']></script>(.*)</token>
                          <value>$1<apex:includeScript value="$2"></apex:includeScript>$3</value>
                          <isRegex>true</isRegex>
                      </filter>
                  </filters>
              </configuration>
              </execution>
          </executions>
        </plugin>
        ...
    </plugins>
  </build>
  ...
</project>
```

## Links

This plugin is based on an Ant script you can find here:
http://force201.wordpress.com/2013/11/06/mapping-an-angularjs-client-into-a-force-com-server/
