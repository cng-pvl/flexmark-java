package com.vladsch.flexmark.docx.converter;

import com.vladsch.flexmark.docx.converter.internal.CoreNodeDocxRenderer;
import com.vladsch.flexmark.docx.converter.internal.DocxLinkResolver;
import com.vladsch.flexmark.docx.converter.util.DocumentContentHandler;
import com.vladsch.flexmark.docx.converter.util.DocxContextImpl;
import com.vladsch.flexmark.docx.converter.util.XmlDocxSorter;
import com.vladsch.flexmark.docx.converter.util.XmlFormatter;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.html.*;
import com.vladsch.flexmark.html.renderer.*;
import com.vladsch.flexmark.util.IRender;
import com.vladsch.flexmark.util.ast.AllNodesVisitor;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.builder.BuilderBase;
import com.vladsch.flexmark.util.builder.Extension;
import com.vladsch.flexmark.util.collection.*;
import com.vladsch.flexmark.util.dependency.FlatDependencyHandler;
import com.vladsch.flexmark.util.html.Attributes;
import com.vladsch.flexmark.util.html.Escaping;
import com.vladsch.flexmark.util.options.*;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.Styles;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Renders a tree of nodes to docx4j API.
 */
@SuppressWarnings("WeakerAccess")
public class DocxRenderer implements IRender {
    public static final DataKey<String> STYLES_XML = new DataKey<>("STYLES_XML", getResourceString("/styles.xml"));
    public static final DataKey<String> NUMBERING_XML = new DataKey<>("NUMBERING_XML", getResourceString("/numbering.xml"));

    public static final DataKey<Boolean> RENDER_BODY_ONLY = new DataKey<>("RENDER_BODY_ONLY", false);
    public static final DataKey<Integer> MAX_IMAGE_WIDTH = new DataKey<>("MAX_IMAGE_WIDTH", 0);

    public static final DataKey<Boolean> DEFAULT_LINK_RESOLVER = new DataKey<>("DEFAULT_LINK_RESOLVER", true);
    public static final DataKey<String> DOC_RELATIVE_URL = new DataKey<>("DOC_RELATIVE_URL", "");
    public static final DataKey<String> DOC_ROOT_URL = new DataKey<>("DOC_ROOT_URL", "");
    public static final DataKey<Boolean> PREFIX_WWW_LINKS = new DataKey<>("PREFIX_WWW_LINKS", true);

    // same keys, same function also available here for convenience
    public static final DataKey<Boolean> RECHECK_UNDEFINED_REFERENCES = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES;
    public static final DataKey<Boolean> PERCENT_ENCODE_URLS = HtmlRenderer.PERCENT_ENCODE_URLS;
    public static final DataKey<Boolean> ESCAPE_HTML = HtmlRenderer.ESCAPE_HTML;
    public static final DataKey<Boolean> ESCAPE_HTML_BLOCKS = HtmlRenderer.ESCAPE_HTML_BLOCKS;
    public static final DataKey<Boolean> ESCAPE_HTML_COMMENT_BLOCKS = HtmlRenderer.ESCAPE_HTML_COMMENT_BLOCKS;
    public static final DataKey<Boolean> ESCAPE_INLINE_HTML = HtmlRenderer.ESCAPE_INLINE_HTML;
    public static final DataKey<Boolean> ESCAPE_INLINE_HTML_COMMENTS = HtmlRenderer.ESCAPE_INLINE_HTML_COMMENTS;
    public static final DataKey<Boolean> SUPPRESS_HTML = HtmlRenderer.SUPPRESS_HTML;
    public static final DataKey<Boolean> SUPPRESS_HTML_BLOCKS = HtmlRenderer.SUPPRESS_HTML_BLOCKS;
    public static final DataKey<Boolean> SUPPRESS_HTML_COMMENT_BLOCKS = HtmlRenderer.SUPPRESS_HTML_COMMENT_BLOCKS;
    public static final DataKey<Boolean> SUPPRESS_INLINE_HTML = HtmlRenderer.SUPPRESS_INLINE_HTML;
    public static final DataKey<Boolean> SUPPRESS_INLINE_HTML_COMMENTS = HtmlRenderer.SUPPRESS_INLINE_HTML_COMMENTS;
    public static final DataKey<Boolean> LINEBREAK_ON_INLINE_HTML_BR = new DataKey<>("LINEBREAK_ON_INLINE_HTML_BR", true);
    public static final DataKey<Boolean> TABLE_CAPTION_TO_PARAGRAPH = new DataKey<>("TABLE_CAPTION_TO_PARAGRAPH", true);
    public static final DataKey<Boolean> TABLE_CAPTION_BEFORE_TABLE = new DataKey<>("TABLE_CAPTION_BEFORE_TABLE", false);
    public static final DataKey<Integer> TABLE_PREFERRED_WIDTH_PCT = new DataKey<>("TABLE_PREFERRED_WIDTH_PCT", 0);
    public static final DataKey<Integer> TABLE_LEFT_INDENT = new DataKey<>("TABLE_LEFT_INDENT", 120);
    public static final DataKey<String> TABLE_STYLE = new DataKey<>("TABLE_STYLE", "");
    public static final DataKey<Boolean> TOC_GENERATE = new DataKey<>("TOC_GENERATE", false);
    public static final DataKey<String> TOC_INSTRUCTION = new DataKey<>("TOC_INSTRUCTION", "TOC \\o \"1-3\" \\h \\z \\u ");
    public static final DataKey<Boolean> LOG_IMAGE_PROCESSING = new DataKey<>("LOG_IMAGE_PROCESSING", false);
    public static final DataKey<Boolean> NO_CHARACTER_STYLES = new DataKey<>("NO_CHARACTER_STYLES", false);
    public static final DataKey<String> CODE_HIGHLIGHT_SHADING = new DataKey<>("CODE_HIGHLIGHT_SHADING", "");
    public static final DataKey<Boolean> ERRORS_TO_STDERR = new DataKey<>("ERRORS_TO_STDERR", false);
    public static final DataKey<String> ERROR_SOURCE_FILE = new DataKey<>("ERROR_SOURCE_FILE", "");
    public static final DataKey<Double> DOC_EMOJI_IMAGE_VERT_OFFSET = new DataKey<>("DOC_EMOJI_IMAGE_VERT_OFFSET", -0.10);  // offset emoji images down by 10% of the line height, final value rounded to nearest pt so can create jumps in position
    public static final DataKey<Double> DOC_EMOJI_IMAGE_VERT_SIZE = new DataKey<>("DOC_EMOJI_IMAGE_VERT_SIZE", 1.05);  // size of image as factor of line height range >0

    // for compatibility with HtmlIdGenerator these are placed here
    public static final DataKey<Boolean> HEADER_ID_GENERATOR_RESOLVE_DUPES = HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES;
    public static final DataKey<String> HEADER_ID_GENERATOR_TO_DASH_CHARS = HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS;
    public static final DataKey<Boolean> HEADER_ID_GENERATOR_NO_DUPED_DASHES = HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES;
    public static final DataKey<Boolean> RENDER_HEADER_ID = HtmlRenderer.RENDER_HEADER_ID;
    public static final DataKey<String> LOCAL_HYPERLINK_SUFFIX = new DataKey<>("LOCAL_HYPERLINK_SUFFIX", "");
    public static final DataKey<String> LOCAL_HYPERLINK_MISSING_HIGHLIGHT = new DataKey<>("LOCAL_HYPERLINK_MISSING_HIGHLIGHT", "red");
    public static final DataKey<String> LOCAL_HYPERLINK_MISSING_FORMAT = new DataKey<>("LOCAL_HYPERLINK_MISSING_FORMAT", "Missing target id: #%s");
    //public static final DataKey<String> FIRST_HEADING_ID_SUFFIX = new DataKey<>("FIRST_HEADING_ID_SUFFIX", "");

    public static final DataKey<String> ASIDE_BLOCK_STYLE = new DataKey<>("ASIDE_BLOCK_STYLE", "AsideBlock");
    public static final DataKey<String> BLOCK_QUOTE_STYLE = new DataKey<>("BLOCK_QUOTE_STYLE", "Quotations");
    public static final DataKey<String> BOLD_STYLE = new DataKey<>("BOLD_STYLE", "StrongEmphasis");
    public static final DataKey<String> DEFAULT_STYLE = new DataKey<>("DEFAULT_STYLE", "Normal");
    public static final DataKey<String> ENDNOTE_ANCHOR_STYLE = new DataKey<>("ENDNOTE_ANCHOR_STYLE", "EndnoteReference");
    public static final DataKey<String> FOOTER = new DataKey<>("FOOTER", "Footer");
    public static final DataKey<String> FOOTNOTE_ANCHOR_STYLE = new DataKey<>("FOOTNOTE_ANCHOR_STYLE", "FootnoteReference");
    public static final DataKey<String> FOOTNOTE_STYLE = new DataKey<>("FOOTNOTE_STYLE", "Footnote");
    public static final DataKey<String> FOOTNOTE_TEXT = new DataKey<>("FOOTNOTE_TEXT", "FootnoteText");
    public static final DataKey<String> HEADER = new DataKey<>("HEADER", "Header");
    public static final DataKey<String> HEADING_1 = new DataKey<>("HEADING_1", "Heading1");
    public static final DataKey<String> HEADING_2 = new DataKey<>("HEADING_2", "Heading2");
    public static final DataKey<String> HEADING_3 = new DataKey<>("HEADING_3", "Heading3");
    public static final DataKey<String> HEADING_4 = new DataKey<>("HEADING_4", "Heading4");
    public static final DataKey<String> HEADING_5 = new DataKey<>("HEADING_5", "Heading5");
    public static final DataKey<String> HEADING_6 = new DataKey<>("HEADING_6", "Heading6");
    public static final DataKey<String> HORIZONTAL_LINE_STYLE = new DataKey<>("HORIZONTAL_LINE_STYLE", "HorizontalLine");
    public static final DataKey<String> HYPERLINK_STYLE = new DataKey<>("HYPERLINK_STYLE", "Hyperlink");
    public static final DataKey<String> INLINE_CODE_STYLE = new DataKey<>("INLINE_CODE_STYLE", "SourceText");
    public static final DataKey<String> INS_STYLE = new DataKey<>("INS_STYLE", "Underlined");
    public static final DataKey<String> ITALIC_STYLE = new DataKey<>("ITALIC_STYLE", "Emphasis");
    public static final DataKey<String> LOOSE_PARAGRAPH_STYLE = new DataKey<>("LOOSE_PARAGRAPH_STYLE", "ParagraphTextBody");
    public static final DataKey<String> PREFORMATTED_TEXT_STYLE = new DataKey<>("PREFORMATTED_TEXT_STYLE", "PreformattedText");
    public static final DataKey<String> STRIKE_THROUGH_STYLE = new DataKey<>("STRIKE_THROUGH_STYLE", "Strikethrough");
    public static final DataKey<String> SUBSCRIPT_STYLE = new DataKey<>("SUBSCRIPT_STYLE", "Subscript");
    public static final DataKey<String> SUPERSCRIPT_STYLE = new DataKey<>("SUPERSCRIPT_STYLE", "Superscript");
    public static final DataKey<String> TABLE_CAPTION = new DataKey<>("TABLE_CAPTION", "TableCaption");
    public static final DataKey<String> TABLE_CONTENTS = new DataKey<>("TABLE_CONTENTS", "TableContents");
    public static final DataKey<String> TABLE_GRID = new DataKey<>("TABLE_GRID", "TableGrid");
    public static final DataKey<String> TABLE_HEADING = new DataKey<>("TABLE_HEADING", "TableHeading");
    public static final DataKey<String> TIGHT_PARAGRAPH_STYLE = new DataKey<>("TIGHT_PARAGRAPH_STYLE", "BodyText");

    // Not used.
    //public static final DataKey<String> BULLET_LIST_STYLE = new DataKey<>("BULLET_LIST_STYLE", "BulletList");
    //public static final DataKey<String> BLOCK_QUOTE_BULLET_LIST_STYLE = new DataKey<>("BLOCK_QUOTE_BULLET_LIST_STYLE", "QuotationsBulletList");
    //public static final DataKey<String> NUMBERED_LIST_STYLE = new DataKey<>("NUMBERED_LIST_STYLE", "NumberedList");
    //public static final DataKey<String> BLOCK_QUOTE_NUMBERED_LIST_STYLE = new DataKey<>("BLOCK_QUOTE_NUMBERED_LIST_STYLE", "QuotationsNumberedList");

    // internal stuff
    public static final String EMOJI_RESOURCE_PREFIX = "emoji:";

    public static final DynamicDefaultKey<String> DOC_EMOJI_ROOT_IMAGE_PATH = new DynamicDefaultKey<String>("DOC_EMOJI_ROOT_IMAGE_PATH", new DataValueFactory<String>() {
        @Override
        public String create(final DataHolder options) {
            if (options != null && options.contains(EmojiExtension.ROOT_IMAGE_PATH)) {
                return options.get(EmojiExtension.ROOT_IMAGE_PATH);
            }

            // kludge it to use our resources
            return EMOJI_RESOURCE_PREFIX;
        }
    });

    final List<NodeDocxRendererFactory> nodeFormatterFactories;
    //final DocxRendererOptions rendererOptions;
    private final DataHolder options;
    private final Builder builder;
    final List<LinkResolverFactory> linkResolverFactories;
    final List<AttributeProviderFactory> attributeProviderFactories;
    final HeaderIdGeneratorFactory htmlIdGeneratorFactory;

    private DocxRenderer(Builder builder) {
        this.builder = new Builder(builder); // take a copy to avoid after creation side effects
        this.options = new DataSet(builder);
        this.htmlIdGeneratorFactory = builder.htmlIdGeneratorFactory;
        //this.rendererOptions = new DocxRendererOptions(this.options);
        this.nodeFormatterFactories = new ArrayList<NodeDocxRendererFactory>(builder.nodeDocxRendererFactories.size() + 1);
        this.nodeFormatterFactories.addAll(builder.nodeDocxRendererFactories);

        // Add as last. This means clients can override the rendering of core nodes if they want.
        this.nodeFormatterFactories.add(new NodeDocxRendererFactory() {
            @Override
            public NodeDocxRenderer create(DataHolder options) {
                return new CoreNodeDocxRenderer(options);
            }
        });

        this.attributeProviderFactories = FlatDependencyHandler.computeDependencies(builder.attributeProviderFactories);
        this.linkResolverFactories = FlatDependencyHandler.computeDependencies(builder.linkResolverFactories);
    }

    @Override
    public DataHolder getOptions() {
        return new DataSet(builder);
    }

    /**
     * Create a new builder for configuring the DocxRenderer.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new builder for configuring the DocxRenderer.
     *
     * @param options initialization options
     * @return a builder
     */
    public static Builder builder(DataHolder options) {
        return new Builder(options);
    }

    public static WordprocessingMLPackage getDefaultTemplate() {
        return getDefaultTemplate("/empty.xml");
    }

    public static WordprocessingMLPackage getDefaultTemplate(String emptyXMLResourcePath) {
        final InputStream inputStream = getResourceInputStream(emptyXMLResourcePath);
        return getDefaultTemplate(inputStream);
    }

    public static WordprocessingMLPackage getDefaultTemplate(InputStream inputStream) {
        try {
            final WordprocessingMLPackage mlPackage = WordprocessingMLPackage.load(inputStream);
            return mlPackage;
        } catch (Docx4JException e) {
            e.printStackTrace();
        }
        return null;
    }

    static void setDefaultStyleAndNumbering(WordprocessingMLPackage out, final DataHolder options) {
        try {
            // (main doc part it if necessary)
            MainDocumentPart documentPart = out.getMainDocumentPart();
            if (documentPart == null) {
                try {
                    documentPart = new MainDocumentPart();
                    out.addTargetPart(documentPart);
                } catch (InvalidFormatException e) {
                    e.printStackTrace();
                }
            }

            if (documentPart.getStyleDefinitionsPart() == null) {
                Part stylesPart = new org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart();
                final Styles styles = (Styles) XmlUtils.unmarshalString(STYLES_XML.getFrom(options));
                ((org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart) stylesPart).setJaxbElement(styles);
                documentPart.addTargetPart(stylesPart); // NB - add it to main doc part, not package!
                assert documentPart.getStyleDefinitionsPart() != null : "Styles failed to set";
            }

            if (documentPart.getNumberingDefinitionsPart() == null) {
                // add it
                Part numberingPart = new org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart();
                final Numbering numbering = (Numbering) XmlUtils.unmarshalString(NUMBERING_XML.getFrom(options));
                ((org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart) numberingPart).setJaxbElement(numbering);
                documentPart.addTargetPart(numberingPart); // NB - add it to main doc part, not package!
                assert documentPart.getNumberingDefinitionsPart() != null : "Numbering failed to set";
            }
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    /**
     * Render a node to the given word processing package
     *
     * @param node   node to render
     * @param output appendable to use for the output
     */
    public void render(Node node, WordprocessingMLPackage output) {
        DocxRenderer.MainDocxRenderer renderer = new DocxRenderer.MainDocxRenderer(options, output, node.getDocument(), null);
        renderer.render(node);
    }

    /**
     * Render a node to the given word processing package
     *
     * @param node   node to render
     * @param output appendable to use for the output
     */
    public void render(Node node, WordprocessingMLPackage output, DocumentContentHandler contentContainer) {
        DocxRenderer.MainDocxRenderer renderer = new DocxRenderer.MainDocxRenderer(options, output, node.getDocument(), contentContainer);
        if (contentContainer != null) {
            contentContainer.startDocumentRendering(renderer);
        }

        renderer.render(node);

        if (contentContainer != null) {
            contentContainer.endDocumentRendering(renderer);
        }
    }

    /**
     * Render the tree of nodes to DocX.
     *
     * @param node the root node
     * @return the rendered HTML
     */
    public String render(Node node) {
        WordprocessingMLPackage mlPackage = getDefaultTemplate();
        render(node, mlPackage);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            mlPackage.save(outputStream, Docx4J.FLAG_SAVE_FLAT_XML);
            final String s = options.get(RENDER_BODY_ONLY) ? XmlFormatter.formatDocumentBody(outputStream.toString("UTF-8"))
                    : XmlDocxSorter.sortDocumentParts(outputStream.toString("UTF-8"));
            return s;
        } catch (Docx4JException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void render(final Node node, final Appendable output) {
        String docx = render(node);
        try {
            output.append(docx);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DocxRenderer withOptions(DataHolder options) {
        return options == null ? this : new DocxRenderer(new Builder(builder, options));
    }

    /**
     * Builder for configuring an {@link DocxRenderer}. See methods for default configuration.
     */
    public static class Builder extends BuilderBase<Builder> implements RendererBuilder {
        List<AttributeProviderFactory> attributeProviderFactories = new ArrayList<AttributeProviderFactory>();
        List<NodeDocxRendererFactory> nodeDocxRendererFactories = new ArrayList<NodeDocxRendererFactory>();
        List<LinkResolverFactory> linkResolverFactories = new ArrayList<LinkResolverFactory>();
        HeaderIdGeneratorFactory htmlIdGeneratorFactory = null;

        public Builder() {
            super();
        }

        public Builder(DataHolder options) {
            super(options);
            loadExtensions();
        }

        public Builder(Builder other) {
            super(other);

            this.attributeProviderFactories.addAll(other.attributeProviderFactories);
            //this.nodeDocxRendererFactories.addAll(other.nodeDocxRendererFactories);
            this.linkResolverFactories.addAll(other.linkResolverFactories);
            this.htmlIdGeneratorFactory = other.htmlIdGeneratorFactory;
        }

        public Builder(Builder other, DataHolder options) {
            this(other);
            withOptions(options);
        }

        @Override
        protected void removeApiPoint(final Object apiPoint) {
            if (apiPoint instanceof AttributeProviderFactory) this.attributeProviderFactories.remove(apiPoint);
            else if (apiPoint instanceof NodeDocxRendererFactory) this.nodeDocxRendererFactories.remove(apiPoint);
            else if (apiPoint instanceof LinkResolverFactory) this.linkResolverFactories.remove(apiPoint);
            else if (apiPoint instanceof HeaderIdGeneratorFactory) this.htmlIdGeneratorFactory = null;
            else {
                throw new IllegalStateException("Unknown data point type: " + apiPoint.getClass().getName());
            }
        }

        @Override
        protected void preloadExtension(final Extension extension) {
            if (extension instanceof DocxRendererExtension) {
                DocxRendererExtension docxRendererExtension = (DocxRendererExtension) extension;
                docxRendererExtension.rendererOptions(this);
            } else if (extension instanceof RendererExtension) {
                RendererExtension docxRendererExtension = (RendererExtension) extension;
                docxRendererExtension.rendererOptions(this);
            }
        }

        @Override
        protected boolean loadExtension(final Extension extension) {
            if (extension instanceof DocxRendererExtension) {
                DocxRendererExtension docxRendererExtension = (DocxRendererExtension) extension;
                docxRendererExtension.extend(this);
                return true;
            } else if (extension instanceof RendererExtension) {
                RendererExtension htmlRendererExtension = (RendererExtension) extension;
                htmlRendererExtension.extend(this, this.get(HtmlRenderer.TYPE));
                return true;
            }
            return false;
        }

        /**
         * @return the configured {@link DocxRenderer}
         */
        public DocxRenderer build() {
            return new DocxRenderer(this);
        }

        /**
         * Add a factory for instantiating a node renderer (done when rendering). This allows to override the rendering
         * of node types or define rendering for custom node types.
         * <p>
         * If multiple node renderers for the same node type are created, the one from the factory that was added first
         * "wins". (This is how the rendering for core node types can be overridden; the default rendering comes last.)
         *
         * @param nodeDocxRendererFactory the factory for creating a node renderer
         * @return {@code this}
         */
        @SuppressWarnings("UnusedReturnValue")
        public Builder nodeFormatterFactory(NodeDocxRendererFactory nodeDocxRendererFactory) {
            this.nodeDocxRendererFactories.add(nodeDocxRendererFactory);
            addExtensionApiPoint(nodeDocxRendererFactory);
            return this;
        }

        /**
         * Add a factory for instantiating a node renderer (done when rendering). This allows to override the rendering
         * of node types or define rendering for custom node types.
         * <p>
         * If multiple node renderers for the same node type are created, the one from the factory that was added first
         * "wins". (This is how the rendering for core node types can be overridden; the default rendering comes last.)
         *
         * @param linkResolverFactory the factory for creating a node renderer
         * @return {@code this}
         */
        @Override
        public Builder linkResolverFactory(LinkResolverFactory linkResolverFactory) {
            this.linkResolverFactories.add(linkResolverFactory);
            addExtensionApiPoint(linkResolverFactory);
            return this;
        }

        /**
         * Add an attribute provider for adding/changing HTML attributes to the rendered tags.
         *
         * @param attributeProviderFactory the attribute provider factory to add
         * @return {@code this}
         */
        @Override
        public Builder attributeProviderFactory(AttributeProviderFactory attributeProviderFactory) {
            this.attributeProviderFactories.add(attributeProviderFactory);
            addExtensionApiPoint(attributeProviderFactory);
            return this;
        }

        /**
         * Add a factory for generating the header id attribute from the header's text
         *
         * @param htmlIdGeneratorFactory the factory for generating header tag id attributes
         * @return {@code this}
         */
        @Override
        public Builder htmlIdGeneratorFactory(HeaderIdGeneratorFactory htmlIdGeneratorFactory) {
            //noinspection VariableNotUsedInsideIf
            if (this.htmlIdGeneratorFactory != null) {
                throw new IllegalStateException("custom header id factory is already set to " + htmlIdGeneratorFactory.getClass().getName());
            }
            this.htmlIdGeneratorFactory = htmlIdGeneratorFactory;
            addExtensionApiPoint(htmlIdGeneratorFactory);
            return this;
        }
    }

    /**
     * Extension for {@link DocxRenderer}.
     */
    public interface DocxRendererExtension extends Extension {
        /**
         * This method is called first on all extensions so that they can adjust the options.
         *
         * @param options option set that will be used for the builder
         */
        void rendererOptions(MutableDataHolder options);

        void extend(Builder builder);
    }

    private final static Iterator<? extends Node> NULL_ITERATOR = new Iterator<Node>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Node next() {
            return null;
        }

        @Override
        public void remove() {
        }
    };

    final static Iterable<? extends Node> NULL_ITERABLE = new Iterable<Node>() {
        @Override
        public Iterator<Node> iterator() {
            return null;
        }
    };

    private class MainDocxRenderer extends DocxContextImpl<Node> implements DocxRendererContext {
        private final Document document;
        private final Map<Class<?>, NodeDocxRendererHandler> renderers;
        private final SubClassingBag<Node> collectedNodes;
        final HashSet<Class<?>> bookmarkWrapsChildren;

        private final List<PhasedNodeDocxRenderer> phasedFormatters;
        private final Set<DocxRendererPhase> renderingPhases;
        private DocxRendererPhase phase;
        Node renderingNode;
        private final LinkResolver[] myLinkResolvers;
        private final HashMap<LinkType, HashMap<String, ResolvedLink>> resolvedLinkMap = new HashMap<LinkType, HashMap<String, ResolvedLink>>();
        private final AttributeProvider[] myAttributeProviders;
        private final HtmlIdGenerator htmlIdGenerator;
        //private final Node firstHeadingNode;
        final TwoWayHashMap<Node, String> nodeIdMap;
        final HashMap<String, Integer> baseIdToSerial;
        final HashMap<String, String> idToValidBookmark;
        final DocxRendererOptions rendererOptions;

        MainDocxRenderer(DataHolder options, WordprocessingMLPackage out, Document document, DocumentContentHandler contentContainer) {
            super(out, new ScopedDataSet(document, options));
            rendererOptions = this.myRendererOptions;
            this.document = document;
            this.renderers = new HashMap<Class<?>, NodeDocxRendererHandler>(32);
            this.renderingPhases = new HashSet<DocxRendererPhase>(DocxRendererPhase.values().length);
            final Set<Class> collectNodeTypes = new HashSet<Class>(100);
            this.phasedFormatters = new ArrayList<PhasedNodeDocxRenderer>(nodeFormatterFactories.size());
            final Boolean defaultLinkResolver = DEFAULT_LINK_RESOLVER.getFrom(options);
            this.myLinkResolvers = new LinkResolver[linkResolverFactories.size() + (defaultLinkResolver ? 1 : 0)];
            this.htmlIdGenerator = htmlIdGeneratorFactory != null ? htmlIdGeneratorFactory.create(this)
                    : new HeaderIdGenerator.Factory().create(this);

            bookmarkWrapsChildren = new HashSet<>();
            //firstHeadingNode = document.getFirstChildAny(Heading.class);
            nodeIdMap = new TwoWayHashMap<>();
            baseIdToSerial = new HashMap<>();
            idToValidBookmark = new HashMap<>();

            setDefaultStyleAndNumbering(out, this.options);

            // we are top level provider
            this.myBlockFormatProviders.put(document, this);
            this.myRunFormatProviders.put(document, this);

            for (int i = 0; i < linkResolverFactories.size(); i++) {
                myLinkResolvers[i] = linkResolverFactories.get(i).create(this);
            }

            if (defaultLinkResolver) {
                // add the default link resolver
                myLinkResolvers[linkResolverFactories.size()] = new DocxLinkResolver.Factory().create(this);
            }

            this.myAttributeProviders = new AttributeProvider[attributeProviderFactories.size()];
            for (int i = 0; i < attributeProviderFactories.size(); i++) {
                myAttributeProviders[i] = attributeProviderFactories.get(i).create(this);
            }

            // The first node renderer for a node type "wins".
            for (int i = nodeFormatterFactories.size() - 1; i >= 0; i--) {
                NodeDocxRendererFactory nodeDocxRendererFactory = nodeFormatterFactories.get(i);
                NodeDocxRenderer nodeDocxRenderer = nodeDocxRendererFactory.create(this.getOptions());
                final Set<NodeDocxRendererHandler<?>> formattingHandlers = nodeDocxRenderer.getNodeFormattingHandlers();
                if (formattingHandlers == null) continue;

                for (NodeDocxRendererHandler nodeType : formattingHandlers) {
                    // Overwrite existing renderer
                    renderers.put(nodeType.getNodeType(), nodeType);
                }

                // get nodes of interest
                Set<Class<?>> nodeClasses = nodeDocxRenderer.getNodeClasses();
                if (nodeClasses != null) {
                    collectNodeTypes.addAll(nodeClasses);
                }

                // get nodes of interest
                Set<Class<?>> wrapChildrenClasses = nodeDocxRenderer.getBookmarkWrapsChildrenClasses();
                if (wrapChildrenClasses != null) {
                    bookmarkWrapsChildren.addAll(wrapChildrenClasses);
                }

                if (nodeDocxRenderer instanceof PhasedNodeDocxRenderer) {
                    Set<DocxRendererPhase> phases = ((PhasedNodeDocxRenderer) nodeDocxRenderer).getFormattingPhases();
                    if (phases != null) {
                        if (phases.isEmpty()) throw new IllegalStateException("PhasedNodeDocxRenderer with empty Phases");
                        this.renderingPhases.addAll(phases);
                        this.phasedFormatters.add((PhasedNodeDocxRenderer) nodeDocxRenderer);
                    } else {
                        throw new IllegalStateException("PhasedNodeDocxRenderer with null Phases");
                    }
                }
            }

            // collect nodes of interest from document
            if (!collectNodeTypes.isEmpty()) {
                NodeCollectingVisitor collectingVisitor = new NodeCollectingVisitor(collectNodeTypes);
                collectingVisitor.collect(document);
                collectedNodes = collectingVisitor.getSubClassingBag();
            } else {
                collectedNodes = null;
            }

            // allow override of content container by caller
            if (contentContainer != null) {
                setContentContainer(contentContainer);
            }
        }

        String calculateNodeId(Node node) {
            String id = htmlIdGenerator.getId(node);
            if (attributeProviderFactories.size() != 0) {
                Attributes attributes = new Attributes();
                if (id != null) attributes.replaceValue("id", id);

                for (AttributeProvider attributeProvider : myAttributeProviders) {
                    attributeProvider.setAttributes(node, AttributablePart.ID, attributes);
                }
                id = attributes.getValue("id");
            }
            return id == null ? "" : id;
        }

        @Override
        public String getNodeId(Node node) {
            String id = nodeIdMap.getSecond(node);

            //if (id != null && node == firstHeadingNode) {
            //    id = id + rendererOptions.firstHeadingIdSuffix;
            //}
            return id;
        }

        @Override
        public String getValidBookmarkName(final String id) {
            String validBookmark = idToValidBookmark.get(id);
            if (validBookmark != null) {
                return validBookmark;
            }

            if (id.length() < 32) {
                idToValidBookmark.put(id, id);
                return id;
            }

            String baseId = id.substring(0, 32);
            if (baseId.endsWith("-") || baseId.endsWith(("_"))) {
                baseId = baseId.substring(0, baseId.length() - 1);
            }
            Integer baseSerial = baseIdToSerial.get(baseId);
            if (baseSerial == null) {
                // first one
                baseSerial = 0;
            }
            baseIdToSerial.put(baseId, baseSerial + 1);
            validBookmark = String.format(Locale.US, "%s-%d", baseId, baseSerial);
            idToValidBookmark.put(id, validBookmark);
            return validBookmark;
        }

        @Override
        public Node getNodeFromId(final String nodeId) {
            return nodeIdMap.getFirst(nodeId);
        }

        @Override
        public String encodeUrl(CharSequence url) {
            if (rendererOptions.percentEncodeUrls) {
                return Escaping.percentEncodeUrl(url);
            } else {
                return String.valueOf(url);
            }
        }

        @Override
        public Attributes extendRenderingNodeAttributes(AttributablePart part, Attributes attributes) {
            Attributes attr = attributes != null ? attributes : new Attributes();
            for (AttributeProvider attributeProvider : myAttributeProviders) {
                attributeProvider.setAttributes(this.renderingNode, part, attr);
            }
            return attr;
        }

        @Override
        public Attributes extendRenderingNodeAttributes(Node node, AttributablePart part, Attributes attributes) {
            Attributes attr = attributes != null ? attributes : new Attributes();
            for (AttributeProvider attributeProvider : myAttributeProviders) {
                attributeProvider.setAttributes(node, part, attr);
            }
            return attr;
        }

        @Override
        public Node getCurrentNode() {
            return renderingNode;
        }

        @Override
        public Node getContextFrame() {
            return renderingNode;
        }

        @Override
        public DataHolder getOptions() {
            return options;
        }

        @Override
        public DocxRendererOptions getDocxRendererOptions() {
            return rendererOptions;
        }

        @Override
        public Document getDocument() {
            return document;
        }

        @Override
        public DocxRendererPhase getPhase() {
            return phase;
        }

        @Override
        public final Iterable<? extends Node> nodesOfType(final Class<?>[] classes) {
            return collectedNodes == null ? NULL_ITERABLE : collectedNodes.itemsOfType(Node.class, classes);
        }

        @Override
        public final Iterable<? extends Node> nodesOfType(final Collection<Class<?>> classes) {
            //noinspection unchecked
            return collectedNodes == null ? NULL_ITERABLE : collectedNodes.itemsOfType(Node.class, classes);
        }

        @Override
        public final Iterable<? extends Node> reversedNodesOfType(final Class<?>[] classes) {
            return collectedNodes == null ? NULL_ITERABLE : collectedNodes.reversedItemsOfType(Node.class, classes);
        }

        @Override
        public final Iterable<? extends Node> reversedNodesOfType(final Collection<Class<?>> classes) {
            //noinspection unchecked
            return collectedNodes == null ? NULL_ITERABLE : collectedNodes.reversedItemsOfType(Node.class, classes);
        }

        @Override
        public ResolvedLink resolveLink(LinkType linkType, CharSequence url, Boolean urlEncode) {
            return resolveLink(linkType, url, (Attributes) null, urlEncode);
        }

        @Override
        public ResolvedLink resolveLink(LinkType linkType, CharSequence url, Attributes attributes, Boolean urlEncode) {
            HashMap<String, ResolvedLink> resolvedLinks = resolvedLinkMap.get(linkType);
            if (resolvedLinks == null) {
                resolvedLinks = new HashMap<String, ResolvedLink>();
                resolvedLinkMap.put(linkType, resolvedLinks);
            }

            String urlSeq = String.valueOf(url);
            ResolvedLink resolvedLink = resolvedLinks.get(urlSeq);
            if (resolvedLink == null) {
                resolvedLink = new ResolvedLink(linkType, urlSeq, attributes);

                if (!urlSeq.isEmpty()) {
                    Node currentNode = renderingNode;

                    for (LinkResolver linkResolver : myLinkResolvers) {
                        resolvedLink = linkResolver.resolveLink(currentNode, this, resolvedLink);
                        if (resolvedLink.getStatus() != LinkStatus.UNKNOWN) break;
                    }

                    if (urlEncode == null && rendererOptions.percentEncodeUrls || urlEncode != null && urlEncode) {
                        resolvedLink = resolvedLink.withUrl(Escaping.percentEncodeUrl(resolvedLink.getUrl()));
                    }
                }

                // put it in the map
                resolvedLinks.put(urlSeq, resolvedLink);
            }

            return resolvedLink;
        }

        @Override
        public void render(final Node node) {
            if (node instanceof Document) {
                htmlIdGenerator.generateIds(document);

                // now create a map of node to id so we can validate hyperlinks
                new AllNodesVisitor() {
                    @Override
                    protected void process(final Node node) {
                        String id = calculateNodeId(node);
                        if (id != null && !id.isEmpty()) {
                            nodeIdMap.add(node, id);
                        }
                    }
                }.visit(document);

                // here we render multiple phases
                for (DocxRendererPhase phase : DocxRendererPhase.values()) {
                    if (phase != DocxRendererPhase.DOCUMENT && !renderingPhases.contains(phase)) { continue; }
                    this.phase = phase;
                    // here we render multiple phases
                    if (this.phase == DocxRendererPhase.DOCUMENT) {
                        NodeDocxRendererHandler nodeRenderer = renderers.get(node.getClass());
                        if (nodeRenderer != null) {
                            renderingNode = node;
                            nodeRenderer.render(node, this);
                            renderingNode = null;
                        }
                    } else {
                        // go through all renderers that want this phase
                        for (PhasedNodeDocxRenderer phasedFormatter : phasedFormatters) {
                            if (phasedFormatter.getFormattingPhases().contains(phase)) {
                                renderingNode = node;
                                phasedFormatter.renderDocument(this, (Document) node, phase);
                                renderingNode = null;
                            }
                        }
                    }
                }
            } else {
                NodeDocxRendererHandler nodeRenderer = renderers.get(node.getClass());

                if (nodeRenderer == null) {
                    nodeRenderer = renderers.get(Node.class);
                }

                if (nodeRenderer != null) {
                    final NodeDocxRendererHandler finalNodeRenderer = nodeRenderer;
                    final Node oldNode = MainDocxRenderer.this.renderingNode;
                    renderingNode = node;

                    contextFramed(new Runnable() {
                        @Override
                        public void run() {
                            final String id = getNodeId(node);
                            if (id != null && !id.isEmpty()) {
                                if (!bookmarkWrapsChildren.contains(node.getClass())) {
                                    final boolean isBlockBookmark = node instanceof Block;
                                    if (isBlockBookmark) {
                                        // put bookmark before the block element
                                        final CTBookmark bookmarkStart = createBookmarkStart(id, true);
                                        createBookmarkEnd(bookmarkStart, true);
                                        finalNodeRenderer.render(renderingNode, MainDocxRenderer.this);
                                    } else {
                                        // wrap bookmark around the inline element
                                        final CTBookmark bookmarkStart = createBookmarkStart(id, false);
                                        finalNodeRenderer.render(renderingNode, MainDocxRenderer.this);
                                        createBookmarkEnd(bookmarkStart, false);
                                    }
                                } else {
                                    finalNodeRenderer.render(renderingNode, MainDocxRenderer.this);
                                }
                            } else {
                                finalNodeRenderer.render(renderingNode, MainDocxRenderer.this);
                            }
                            renderingNode = oldNode;
                        }
                    });
                } else {
                    // default behavior is controlled by generic Node.class that is implemented in CoreNodeDocxRenderer
                    throw new IllegalStateException("Core Node DocxRenderer should implement generic Node renderer");
                }
            }
        }

        void renderChildrenUnwrapped(final Node parent) {
            Node node = parent.getFirstChild();
            while (node != null) {
                Node next = node.getNext();
                render(node);
                node = next;
            }
        }

        public void renderChildren(final Node parent) {
            final String id = getNodeId(parent);
            if (id != null && !id.isEmpty()) {
                if (bookmarkWrapsChildren.contains(parent.getClass())) {
                    final CTBookmark bookmarkStart = createBookmarkStart(id, false);
                    renderChildrenUnwrapped(parent);
                    createBookmarkEnd(bookmarkStart, false);
                    //contextFramed(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //    }
                    //});
                } else {
                    renderChildrenUnwrapped(parent);
                }
            } else {
                renderChildrenUnwrapped(parent);
            }
        }

        @Override
        public Node getProviderFrame() {
            return document;
        }
    }

    public static String getResourceString(String resourcePath) {
        try {
            InputStream stream = getResourceInputStream(resourcePath);
            StringBuilder sb = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }

            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream getResourceInputStream(String resourcePath) {
        String specPath = resourcePath != null ? resourcePath : "/spec.txt";
        InputStream stream = DocxRenderer.class.getResourceAsStream(specPath);
        if (stream == null) {
            throw new IllegalStateException("Could not load " + resourcePath + " classpath resource");
        }
        return stream;
    }
}
