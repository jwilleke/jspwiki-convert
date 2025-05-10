package com.willeke.jspwikiconverter;

/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

import org.apache.commons.lang3.ArrayUtils;
import org.apache.wiki.TestEngine;
import org.apache.wiki.api.core.Attachment;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.ContextEnum;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.attachment.AttachmentManager;
import org.apache.wiki.htmltowiki.HtmlStringToWikiTranslator;
import org.apache.wiki.markdown.migration.parser.JSPWikiToMarkdownMarkupParser;
import org.apache.wiki.pages.PageManager;
import org.apache.wiki.plugin.PluginManager;
import org.apache.wiki.render.RenderingManager;
import org.jdom2.JDOMException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.wiki.TestEngine.with;

/**
 * <p>
 * Class used to autogenerate the initial set of markdown files, derived from
 * the ones with jspwiki syntax, as part
 * of the build,
 * </p>
 * <p>
 * Can be used as a starting point to develop more complex converters from
 * jspwiki to markdown syntax f.ex, to also
 * convert page history, retain original authors, etc.
 * </p>
 */
public class WikiSyntaxConverter {

  /**
   * Constructor for WikiSyntaxConverter.
   *
   * @param wikiPages     The directory containing JSPWiki pages.
   * @param markdownPages The directory to save the converted Markdown files.
   * @param lang          The language code (e.g., "de", "en").
   */
  public WikiSyntaxConverter(final String wikiPages, final String markdownPages, final String lang) {
  }

  /**
   * Converts JSPWiki syntax files to Markdown for the specified language.
   * The generated Markdown files are saved in the
   * {@code ../jspwiki-wikipages/{lang}/src/main/resources/markdown} directory.
   * Any existing content in this directory, including properties files, will be
   * deleted before the conversion.
   *
   * @param lang The language code (e.g., "de", "en").
   * @throws Exception If any error occurs during the conversion process.
   */
  public void convertJspwikiToMarkdown(final String wikiPages, final String markdownPages, final String lang)
      throws Exception {
    final File target = new File(wikiPages + lang + markdownPages);
    if (target.exists()) {
      System.out.println("Target directory (markdownPages): " + markdownPages);
    }
    translateJSPWikiToMarkdown(wikiPages, markdownPages, lang);
  }

  private void translateJSPWikiToMarkdown(final String wikiPages, final String markdownPages, final String lang)
      throws ProviderException {
    final Engine jspw = buildEngine("jspwiki", wikiPages);
    final Engine md = buildEngine("markdown", markdownPages);
    jspw.getManager(PluginManager.class).enablePlugins(false);

    final Collection<Page> pages = jspw.getManager(PageManager.class).getAllPages();
    int total = 0;
    int success = 0;
    int failed = 0;

    for (final Page p : pages) {
      try {
        final Context context = Wiki.context().create(jspw, p);
        System.out.println("Processing page: " + p.getName());
        total++;
        context.setRequestContext(ContextEnum.PAGE_NONE.getRequestContext());
        context.setVariable(Context.VAR_WYSIWYG_EDITOR_MODE, Boolean.TRUE);
        final String pagedata = jspw.getManager(PageManager.class).getPureText(p.getName(), p.getVersion());
        
        final String html = jspw.getManager(RenderingManager.class).textToHTML(context, pagedata, null, null, null,            false,            false);
        System.out.println("HTML content for page " + p.getName() + ": " + html); // Debug output
        final String syntax = new HtmlStringToWikiTranslator(md).translate(html);
        
        final Context contextMD = Wiki.context().create(md, p);
        md.getManager(PageManager.class).saveText(contextMD, clean(syntax));
        final List<Attachment> attachments = jspw.getManager(AttachmentManager.class).listAttachments(p);
        for (final Attachment attachment : attachments) {
          final InputStream bytes = jspw.getManager(AttachmentManager.class).getAttachmentStream(context, attachment);
          md.getManager(AttachmentManager.class).storeAttachment(attachment, bytes);
        }
      } catch (ProviderException e) {
        failed++;
        System.err.println("ProviderException occurred: " + e.getMessage());
        e.printStackTrace();

      } catch (IllegalArgumentException e) {
        failed++;
        System.err.println("IllegalArgumentException occurred: " + e.getMessage());
        e.printStackTrace();

      } catch (NullPointerException e) {
        failed++;
        System.err.println("NullPointerException occurred: " + e.getMessage());
        e.printStackTrace();

      } catch (ClassCastException e) {
        failed++;
        System.err.println("ClassCastException occurred: " + e.getMessage());
        e.printStackTrace();

      } catch (JDOMException e) {
        failed++;
        System.err.println("JDOMException occurred: " + e.getMessage());
        e.printStackTrace();
      } catch (IOException e) {
        failed++;
        System.err.println("IOException occurred: " + e.getMessage());
        e.printStackTrace();
      }
      // Code that might throw ReflectiveOperationException
      catch (ReflectiveOperationException e) {
        failed++;
        System.err.println("ReflectiveOperationException occurred: " + e.getMessage());
        e.printStackTrace();
      }
      
      // Code that might throw WikiException
      catch (WikiException e) {
        failed++;
        System.err.println("WikiException occurred: " + e.getMessage());
        e.printStackTrace();
      }
      System.out.println("Migration complete.");
      System.out.println("Total pages: " + total);
      System.out.println("Successfully translated: " + success);
      System.out.println("Failed: " + failed);
    }
  }

  /**
   * Builds a JSPWiki engine with the specified syntax and page directory.
   *
   * @param syntax  The syntax to be used (e.g., "jspwiki", "markdown").
   * @param pageDir The directory where the pages are stored.
   * @return A configured JSPWiki engine.
   */
  private Engine buildEngine(final String syntax, final String pageDir) {
    return TestEngine.build(with("jspwiki.fileSystemProvider.pageDir", pageDir),
        with(RenderingManager.PROP_PARSER, JSPWikiToMarkdownMarkupParser.class.getName()), // will be overwritten if
                                                                                           // jspwiki.syntax=markdown
        with("jspwiki.test.disable-clean-props", "true"),
        with("jspwiki.workDir", "./target/workDir" + syntax),
        with("appender.rolling.fileName", "./target/wiki-" + syntax + ".log"),
        with("jspwiki.cache.enable", "false"),
        with("jspwiki.syntax", syntax));
  }

  private String clean(final String wikiSyntax) {
    return wikiSyntax;
  }

  /**
   * Deletes the specified directory and all its contents.
   *
   * @param directoryToBeDeleted The directory to be deleted.
   */
  private void deleteDirectory(File directoryToBeDeleted) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
      for (File file : allContents) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          file.delete();
        }
      }
    }
    directoryToBeDeleted.delete();
  }

  /**
   * Main method to run the converter.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {
    WikiSyntaxConverter converter = new WikiSyntaxConverter(args[0], args[1], args[2]);
    // String[] languages = { "de", "en", "es", "fi", "fr", "it", "nl", "pt_BR",
    // "ru", "zh_CN" };
    if (args.length < 3) {
      System.out.println("Usage: java WikiMigrationRunner <sourceDir> <targetDir> <lang>");
      System.exit(1);
    }
    // wikiPages + lang + markdownPages
    final String wikiPages = new File(args[0]).getAbsolutePath();
    final String markdownPages = new File(args[1]).getAbsolutePath();
    final String lang = args[2];
    System.out.println("Source directory (wikiPages): " + wikiPages);
    System.out.println("Target directory (markdownPages): " + markdownPages);
    try {
      converter.convertJspwikiToMarkdown(wikiPages, markdownPages, lang);
    } catch (Exception e) {
      System.err.println("Error processing: " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println("Successfully converted JSPWiki to Markdown for language: " + lang);
    System.out.println("Conversion process completed.");
  }
}
