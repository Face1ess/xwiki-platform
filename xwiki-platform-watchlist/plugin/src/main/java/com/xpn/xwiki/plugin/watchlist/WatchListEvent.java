/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.plugin.watchlist;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.ecs.html.Div;
import org.apache.ecs.html.Span;
import org.suigeneris.jrcs.rcs.Version;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.AttachmentDiff;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.ObjectDiff;
import com.xpn.xwiki.plugin.activitystream.api.ActivityEventType;
import com.xpn.xwiki.plugin.activitystream.plugin.ActivityEvent;
import com.xpn.xwiki.plugin.diff.DiffPluginApi;

/**
 * The class representing an event in the WatchList. The current implementation is a wrapper for one or more
 * ActivityEvent.
 * 
 * @version $Id$
 */
public class WatchListEvent implements Comparable<WatchListEvent>
{
    /**
     * Prefix used in inline style we put in HTML diffs.
     */
    private static final String HTML_STYLE_PLACEHOLDER_PREFIX = "WATCHLIST_STYLE_DIFF_";

    /**
     * Event hashcode.
     */
    private final int hashCode;

    /**
     * Prefixed space in which the event happened.
     */
    private final String prefixedSpace;

    /**
     * Prefixed document fullName in which the event happened.
     */
    private final String prefixedFullName;

    /**
     * Type of the event (example: "update").
     */
    private String type;

    /**
     * Wrapped events.
     */
    private List<ActivityEvent> activityEvents = new ArrayList<ActivityEvent>();

    /**
     * Version of the document before the event happened.
     */
    private String previousVersion;

    /**
     * List of versions affected by this event. It will contain only one entry if the event is not a composite event.
     */
    private List<String> versions;

    /**
     * List of authors for this event. It will contain only one entry if the event is not a composite event.
     */
    private List<String> authors;

    /**
     * List of dates for this event. It will contain only one entry if the event is not a composite event.
     */
    private List<Date> dates;

    /**
     * Difference generated by update events in a document, formatted in HTML.
     */
    private String htmlDiff;

    /**
     * Constructor.
     * 
     * @param activityEvent activity stream event to wrap
     */
    public WatchListEvent(ActivityEvent activityEvent)
    {
        this.activityEvents.add(activityEvent);
        type = activityEvent.getType();
        prefixedSpace = activityEvent.getWiki() + WatchListStore.WIKI_SPACE_SEP + activityEvent.getSpace();
        prefixedFullName = activityEvent.getWiki() + WatchListStore.WIKI_SPACE_SEP + activityEvent.getPage();

        int hash = 3;
        if (ActivityEventType.UPDATE.equals(activityEvent)) {
            hashCode = 42 * hash + prefixedFullName.hashCode() + activityEvent.getType().hashCode();
        } else {
            hashCode =
                42 * hash + prefixedFullName.hashCode() + activityEvent.getType().hashCode()
                    + activityEvent.getDate().hashCode();
        }
    }

    /**
     * Add another event associated to this event.
     * 
     * @param event The event to add.
     */
    public void addEvent(WatchListEvent event)
    {
        if (ActivityEventType.DELETE.equals(event.getType())) {
            // If the document has been deleted, reset this event
            activityEvents.clear();
            type = event.getType();
            versions.clear();
            versions = null;
            authors.clear();
            authors = null;
            previousVersion = null;
            htmlDiff = null;
        } else if (ActivityEventType.UPDATE.equals(event.getType()) && ActivityEventType.DELETE.equals(getType())) {
            // If an update event had been fired before a delete, discard it
            return;
        }

        activityEvents.add(event.getActivityEvent());
    }

    /**
     * @return The wiki in which the event happened.
     */
    public String getWiki()
    {
        return getActivityEvent().getWiki();
    }

    /**
     * @return The space in which the event happened.
     */
    public String getSpace()
    {
        return getActivityEvent().getSpace();
    }

    /**
     * @return The space, prefixed with the wiki name, in which the event happened (example: "xwiki:Main").
     */
    public String getPrefixedSpace()
    {
        return prefixedSpace;
    }

    /**
     * @return The fullName of the document which has generated this event (example: "Main.WebHome").
     */
    public String getFullName()
    {
        return getActivityEvent().getPage();
    }

    /**
     * @return The fullName of the document which has generated this event, prefixed with the wiki name. Example:
     *         "xwiki:Main.WebHome".
     */
    public String getPrefixedFullName()
    {
        return prefixedFullName;
    }

    /**
     * @return The date when the event occurred.
     */
    public Date getDate()
    {
        return getActivityEvent().getDate();
    }

    /**
     * @return Get all the dates of a composite event, if this event is not a composite this list will contain single
     *         entry.
     */
    public List<Date> getDates()
    {
        if (dates == null) {
            dates = new ArrayList<Date>();

            if (!isComposite()) {
                dates.add(getDate());
            } else {
                for (ActivityEvent event : activityEvents) {
                    dates.add(event.getDate());
                }
            }
        }

        return dates;
    }

    /**
     * @return The type of this event (example: "update", "delete").
     */
    public String getType()
    {
        return type;
    }

    /**
     * @return The underlying ActivityEvent.
     */
    private ActivityEvent getActivityEvent()
    {
        return activityEvents.get(0);
    }

    /**
     * @return The user who generated the event.
     */
    public String getAuthor()
    {
        return getActivityEvent().getUser();
    }

    /**
     * @return Get all the authors of a composite event, if this event is not a composite this list will contain single
     *         entry.
     */
    public List<String> getAuthors()
    {
        if (authors == null) {
            authors = new ArrayList<String>();

            if (!isComposite()) {
                authors.add(getAuthor());
            } else {
                for (ActivityEvent event : activityEvents) {
                    if (!authors.contains(event.getUser())) {
                        authors.add(event.getUser());
                    }
                }
            }
        }

        return authors;
    }

    /**
     * @return The version of the document at the time it has generated the event.
     */
    public String getVersion()
    {
        return getActivityEvent().getVersion();
    }

    /**
     * @return All the versions from a composite event, if the event is not a composite the list will contain a single
     *         entry
     */
    public List<String> getVersions()
    {
        if (versions == null) {
            versions = new ArrayList<String>();

            if (!isComposite()) {
                if (!StringUtils.isBlank(getActivityEvent().getVersion())) {
                    versions.add(getActivityEvent().getVersion());
                }
            } else {
                for (ActivityEvent event : activityEvents) {
                    if (!StringUtils.isBlank(event.getVersion()) && !versions.contains(event.getVersion())) {
                        versions.add(event.getVersion());
                    }
                }
            }
        }

        return versions;
    }

    /**
     * @param context The XWiki context
     * @return The version of the document which has generated the event, before the actual event.
     */
    public String getPreviousVersion(XWikiContext context)
    {
        if (previousVersion == null) {
            String currentVersion = "";
            previousVersion = "1.1";

            try {
                if (!isComposite()) {
                    currentVersion = getActivityEvent().getVersion();
                } else {
                    List<String> allVersions = getVersions();
                    if (allVersions.size() > 1) {
                        currentVersion = allVersions.get(allVersions.size() - 1);
                    }
                }

                if (!StringUtils.isBlank(currentVersion)) {
                    XWikiDocument doc = context.getWiki().getDocument(prefixedFullName, context);
                    XWikiDocument docRev = context.getWiki().getDocument(doc, currentVersion, context);
                    doc.loadArchive(context);
                    Version version = doc.getDocumentArchive().getPrevVersion(docRev.getRCSVersion());
                    if (version != null) {
                        previousVersion = version.toString();
                    }
                }
            } catch (XWikiException e) {
                // Catch the exception to be sure we won't send emails containing stacktraces to users.
                e.printStackTrace();
            }
        }

        return previousVersion;
    }

    /**
     * @return True if the event is made of multiple events.
     */
    public boolean isComposite()
    {
        return activityEvents.size() > 1;
    }

    /**
     * @param classAttr The class of the div to create
     * @return a HTML div element
     */
    private Div createDiffDiv(String classAttr)
    {
        Div div = new Div();
        div.setClass(classAttr);
        div.setStyle(HTML_STYLE_PLACEHOLDER_PREFIX + classAttr);

        return div;
    }

    /**
     * @param classAttr The class of the span to create
     * @return an opening span markup
     */
    private Span createDiffSpan(String classAttr)
    {
        Span span = new Span();
        span.setClass(classAttr);
        span.setStyle(HTML_STYLE_PLACEHOLDER_PREFIX + classAttr);

        return span;
    }

    /**
     * @param objectDiffs List of object diff
     * @param prefix prefix to apply to HTML classnames
     * @param diff the diff plugin API
     * @return The HTML diff
     */
    private String getHTMLObjectsDiff(List<List<ObjectDiff>> objectDiffs, String prefix, DiffPluginApi diff)
    {
        StringBuffer result = new StringBuffer();
        String propSeparator = ": ";

        try {
            for (List<ObjectDiff> oList : objectDiffs) {
                if (oList.size() > 0) {
                    Div mainDiv = createDiffDiv(prefix + "Diff");
                    Span objectName = createDiffSpan(prefix + "ClassName");
                    objectName.addElement(oList.get(0).getClassName());
                    mainDiv.addElement(objectName);
                    for (ObjectDiff oDiff : oList) {
                        if (!StringUtils.isBlank(oDiff.getPropName())) {
                            Div propDiv = createDiffDiv("propDiffContainer");
                            Span propNameSpan = createDiffSpan("propName");
                            propNameSpan.addElement(oDiff.getPropName() + propSeparator);
                            propDiv.addElement(propNameSpan);
                            String propDiff =
                                diff.getDifferencesAsHTML(oDiff.getPrevValue().toString(), oDiff.getNewValue()
                                    .toString());
                            Div propDiffDiv = createDiffDiv("propDiff");
                            propDiffDiv.addElement(propDiff);
                            propDiv.addElement(propDiffDiv);
                            mainDiv.addElement(propDiv);
                        }
                    }
                    result.append(mainDiv);
                }
            }
        } catch (XWikiException e) {
            // Catch the exception to be sure we won't send emails containing stacktraces to users.
            e.printStackTrace();
        }

        return result.toString();
    }

    /**
     * @param context The XWiki context
     * @return The diff, formated in HTML, to display to the user when a document has been updated
     */
    public String getHTMLDiff(XWikiContext context)
    {
        if (htmlDiff == null) {
            try {
                XWikiDocument d2 = context.getWiki().getDocument(getPrefixedFullName(), context);
                XWikiDocument d1 = context.getWiki().getDocument(d2, getPreviousVersion(context), context);
                DiffPluginApi diff = (DiffPluginApi) context.getWiki().getPluginApi("diff", context);
                StringBuffer result = new StringBuffer();
                String contentDiff = diff.getDifferencesAsHTML(d1.getContent(), d2.getContent());
                List<AttachmentDiff> attachDiffs = d2.getAttachmentDiff(d1, d2, context);
                List<List<ObjectDiff>> objectDiffs = d2.getObjectDiff(d1, d2, context);
                List<List<ObjectDiff>> classDiffs = d2.getClassDiff(d1, d2, context);

                if (!StringUtils.isBlank(contentDiff)) {
                    Div contentDiv = createDiffDiv("contentDiff");
                    contentDiv.addElement(contentDiff);
                    result.append(contentDiv);
                }

                for (AttachmentDiff aDiff : attachDiffs) {
                    Div attachmentDiv = createDiffDiv("attachmentDiff");
                    attachmentDiv.addElement(aDiff.toString());
                    result.append(attachmentDiv);
                }

                result.append(getHTMLObjectsDiff(objectDiffs, "object", diff));
                result.append(getHTMLObjectsDiff(classDiffs, "class", diff));

                htmlDiff = result.toString();
            } catch (XWikiException e) {
                // Catch the exception to be sure we won't send emails containing stacktraces to users.
                e.printStackTrace();
            }
        }

        return htmlDiff;
    }

    /**
     * Perform a string comparison on the prefixed fullName of the source document.
     * 
     * @param event event to compare with
     * @return the result of the string comparison
     */
    public int compareTo(WatchListEvent event)
    {
        return getPrefixedFullName().compareTo(event.getPrefixedFullName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return hashCode;
    }

    /**
     * Overriding of the default equals method.
     * 
     * @param obj the ActivityEvent to be compared with
     * @return True if the two events have been generated by the same document and are equals or conflicting
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof WatchListEvent)) {
            return false;
        }
 
        // At first this method was returning true when the documents were the same and the events were the same type.
        // Since we don't want to keep update events for documents that have been deleted this method has been modified
        // to a point were it performs something different from a equals(), it returns true when obj is a delete event
        // and 'this' is an update event. See WatchListEventManager#WatchListEventManager(Date, XWikiContext).
        // TODO: refactoring.
        WatchListEvent event = ((WatchListEvent) obj);
        return prefixedFullName.equals(event.getPrefixedFullName()) && WatchListEventType.UPDATE.equals(getType())
            && (WatchListEventType.UPDATE.equals(event.getType()) || WatchListEventType.DELETE.equals(event.getType()));
    }
}
