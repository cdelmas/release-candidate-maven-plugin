package com.smartcodeltd;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.smartcodeltd.domain.Version;
import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * <p>
 * Updates the <code>pom.xml</code> project version as per the configured <code>releaseVersionFormat</code> template.
 * </p>
 *
 * <p>
 * The original structure of the <code>pom.xml</code> file is preserved when the file is updated
 * (including the comments, whitespace, formatting, etc.),
 * so that <a href="https://github.com/smartcodeltd/release-candidate-maven-plugin/blob/ac0037773095f5501b5c5af6612b7327335a6ef9/src/test/java/com/smartcodeltd/UpdateVersionMojoTest.java#L90">the only thing that changes</a> is the version number.
 * </p>
 */
@Mojo(name = "updateVersion")
public class UpdateVersionMojo
        extends ReleaseCandidateMojo
{
    private final static String default_version_format  = "{{ version }}";

    /**
     * <p>
     * Describes how the pom.xml project version should be expanded into a new version number.
     * </p>
     *
     * <p>
     * For example, assume:
     * </p>
     *
     * <ul>
     *     <li>that <code>pom.xml</code> project version is set to <code>1.2.0-beta-SNAPSHOT</code></li>
     *     <li>and that the current date is 2015-08-01</li>
     * </ul>
     *
     * <p>
     * Below table depicts the relationship between the <code>releaseVersionFormat</code>
     * and what project version gets expanded into when <code>release-candidate:updateVersion</code> is called:
     * </p>
     *
     * <table summary="The relationship between the releaseVersionFormat and what project version gets expanded into">
     *     <thead>
     *     <tr>
     *       <th><code>releaseVersionFormat</code></th>
     *       <th>Result</th>
     *       <th>Outcome</th>
     *     </tr>
     *     </thead>
     *     <tbody>
     *       <tr>
     *         <td><code>{{&nbsp;version&nbsp;}}</code></td>
     *         <td><code>1.2.0-beta-SNAPSHOT</code></td>
     *         <td>Full version number, as is</td>
     *       </tr>
     *       <tr>
     *         <td><code>{{&nbsp;api_version&nbsp;}}</code></td>
     *         <td><code>1.2.0</code></td>
     *         <td>The API version</td>
     *       </tr>
     *       <tr>
     *         <td><code>{{&nbsp;qualified_api_version&nbsp;}}</code></td>
     *         <td><code>1.2.0-beta</code></td>
     *         <td>API version including a developer-defined <a href="https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#MAVEN400">qualifier</a></td>
     *       </tr>
     *       <tr>
     *         <td><code>{{&nbsp;timestamp('YYYYMMdd')&nbsp;}}</code></td>
     *         <td><code>20150801</code></td>
     *         <td>Current time; accepts a <a href="http://joda-time.sourceforge.net/apidocs/org/joda/time/format/DateTimeFormat.html">JodaTime-compatible</a>
     *             timestamp format definition</td>
     *       </tr>
     *     </tbody>
     * </table>
     *
     * <p>
     * Above tokens can also be used together, for example:
     * </p>
     *
     * <table summary="Using multiple tokens together">
     *     <thead>
     *     <tr>
     *       <th><code>releaseVersionFormat</code></th>
     *       <th>Result</th>
     *     </tr>
     *     </thead>
     *     <tbody>
     *       <tr>
     *         <td><code>{{&nbsp;qualified_api_version&nbsp;}}-{{&nbsp;timestamp('YYYYMMdd')&nbsp;}}</code></td>
     *         <td><code>1.2.0-beta-20150801</code></td>
     *       </tr>
     *       <tr>
     *         <td><code>{{&nbsp;qualified_api_version&nbsp;}}-built.{{&nbsp;timestamp('YYYYMMdd')&nbsp;}}</code></td>
     *         <td><code>1.2.0-beta-built.20150801</code></td>
     *       </tr>
     *     </tbody>
     * </table>
     *
     * <p>
     * <strong>Please note</strong> that <a href="http://books.sonatype.com/mvnref-book/reference/resource-filtering-sect-properties.html#resource-filtering-sect-user-defined">Maven properties</a> can be used as well.
     * This way if you:
     * </p>
     *
     * <ul>
     *   <li>provide, say a <code>build_number</code> parameter when you build your project:<br /><code>mvn clean package -Dbuild_number=176</code></li>
     *   <li>and given that you set <code>releaseVersionFormat</code> to say:<br /><code>{{&nbsp;qualified_api_version&nbsp;}}-build.${build_number}</code></li>
     *   <li>then the resulting version number will become:<br /><code>1.2.0-beta-build.176</code></li>
     * </ul>
     */
    @Parameter(defaultValue = default_version_format, required = false, property = "releaseVersionFormat")
    private String releaseVersionFormat;

    public void execute()
            throws MojoExecutionException
    {
        File   pom        = project.getFile();
        String newVersion = evaluated(versionOf(root(project)));

        info("Setting version to: '%s'", newVersion);

        try {
            update(pom, with(newVersion));
        }
        catch (IOException e) {
            throw new MojoExecutionException(String.format("Couldn't write to pom file '%s'", pom.getPath()), e);
        }
    }

    // --

    private String evaluated(Version version) {
        return version.formattedWith(releaseVersionFormat);
    }

    private void update(File pom, String newVersion) throws IOException {
        Document doc = parsed(pom);

        firstExisting(
                parentVersion(doc),
                projectVersion(doc)
        ).setText(newVersion);

        Files.write(doc.toString(), pom, charset);
    }

    private Element projectVersion(Document doc) {
        return doc.getChild("project/version");
    }

    private Element parentVersion(Document doc) {
        if(project.hasParent()
                && !isOrganizationPom(project.getParent())
                && isSnapshot(project.getParent())
                && sameGroupId(project, project.getParent())) {
            return doc.getChild("project/parent/version");
        } else {
            return null;
        }
    }

    private boolean sameGroupId(MavenProject project, MavenProject parent) {
       return project.getGroupId().equals(parent.getGroupId());
    }

    private boolean isSnapshot(MavenProject project) {
        return project.getVersion().endsWith("SNAPSHOT");
    }

    private boolean isOrganizationPom(MavenProject parent) {
        return "pom".equals(parent.getPackaging()) && parent.getModules().isEmpty();
    }

    private Element firstExisting(Element... elements) {
        return Iterables.find(asList(elements), Predicates.notNull(), new Element("dummy"));
    }
}