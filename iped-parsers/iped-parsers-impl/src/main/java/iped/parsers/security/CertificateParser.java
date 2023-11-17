package iped.parsers.security;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;

public class CertificateParser extends AbstractParser {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    public static final MediaType X509_MIME = MediaType.application("x-x509-cert");

    // these are old mimetypes before TIKA had implemented its mimetype
    // x-x509-ca-cert
    @Deprecated
	public static final MediaType PEM_MIME = MediaType.application("x-pem-file");
    @Deprecated
    public static final MediaType DER_MIME = MediaType.application("pkix-cert");

    private static final MediaType PKCS7_MIME = MediaType.application("pkcs7-mime");
    public static final MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");
    private static Set<MediaType> SUPPORTED_TYPES = null;

    public static final Property NOTBEFORE = Property.internalDate("certificate:notBefore"); //$NON-NLS-1$
    public static final Property NOTAFTER = Property.internalDate("certificate:notAfter"); //$NON-NLS-1$
    public static final String ISSUER = "certificate:issuer"; //$NON-NLS-1$
    public static final String X500_ISSUER = "certificate:X500Issuer";
    public static final String SUBJECT = "certificate:subject"; //$NON-NLS-1$
    public static final String X500_SUBJECT = "certificate:X500Subject"; //$NON-NLS-1$
    public static final String SIGNERS = "certificate:Signers"; //$NON-NLS-1$
    public static final Property ISSUBJECTAUTHORITY = Property.internalBoolean("certificate:subjectIsCertAuthority"); //$NON-NLS-1$
    public static final String NOALTNAMES = "This certificate has no alternative names.";

    
    public CertificateParser() {
        MetadataUtil.setMetadataType(NOTBEFORE.getName(), Date.class);
        MetadataUtil.setMetadataType(NOTAFTER.getName(), Date.class);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        if (SUPPORTED_TYPES == null) {
            Set<MediaType> set = new HashSet<>();
            set.add(PEM_MIME);
            set.add(DER_MIME);
            set.add(X509_MIME);
            set.add(PKCS7_MIME);
            set.add(PKCS7_SIGNATURE);
            SUPPORTED_TYPES = set;
        }

        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            X509Certificate cert = null;
            String mimeType = metadata.get("Indexer-Content-Type");

            if (mimeType.equals(PKCS7_SIGNATURE.toString())) {
                EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                        new ParsingEmbeddedDocumentExtractor(context));
                try (InputStream certStream = new FileInputStream(file)) {
                    CertPath p = cf.generateCertPath(certStream, "PKCS7");
                    List certs = p.getCertificates();

                    // extracts certificates
                    for (Iterator iterator = certs.iterator(); iterator.hasNext();) {
                        cert = (X509Certificate) iterator.next();

                        Metadata certMetadata = new Metadata();
                        certMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY,
                                cert.getSubjectX500Principal().getName());

                        extractor.parseEmbedded(new ByteArrayInputStream(cert.getEncoded()), new DefaultHandler(),
                                certMetadata,
                                true);
                    }
                }
                try (InputStream certStream = new FileInputStream(file)) {
                    DigestCalculatorProvider digestCalculatorProvider = new JcaDigestCalculatorProviderBuilder()
                            .setProvider("BC").build();
                    CMSSignedDataParser sp = new CMSSignedDataParser(digestCalculatorProvider, stream);
                    // extracts certificates
                    Store certStore = sp.getCertificates();
                    SignerInformationStore signers = sp.getSignerInfos();

                    Collection c = signers.getSigners();
                    Iterator it = c.iterator();

                    List<X509CertificateHolder> certificates = new ArrayList<X509CertificateHolder>();
                    while (it.hasNext()) {
                        SignerInformation signer = (SignerInformation) it.next();
                        Collection certCollection = certStore.getMatches(signer.getSID());

                        Iterator certIt = certCollection.iterator();
                        X509CertificateHolder certHolder = (X509CertificateHolder) certIt.next();

                        metadata.add(SIGNERS,
                                certHolder.getSubject().toString());
                    }
                }
            } else {
                InputStream certStream = null;
                try {
                    certStream = new FileInputStream(file);
                    cert = (X509Certificate) cf.generateCertificate(certStream);
                } finally {
                    if (certStream != null) {
                        certStream.close();
                    }
                }
                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.startElement("head"); //$NON-NLS-1$
                xhtml.startElement("style"); //$NON-NLS-1$
                xhtml.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
                xhtml.endElement("style"); //$NON-NLS-1$
                xhtml.endElement("head"); //$NON-NLS-1$
                generateCertificateHtml(cert, xhtml);
                xhtml.endDocument();

                metadata.set(NOTBEFORE, cert.getNotBefore());
                metadata.set(NOTAFTER, cert.getNotAfter());

                metadata.set(X500_ISSUER, cert.getIssuerX500Principal().getName());
                metadata.set(ISSUER, cert.getIssuerDN().getName());
                metadata.set(X500_SUBJECT, cert.getSubjectX500Principal().getName());
                metadata.set(SUBJECT, cert.getSubjectDN().getName());
                if (cert.getBasicConstraints() <= -1) {
                    metadata.set(ISSUBJECTAUTHORITY, Boolean.FALSE.toString());
                } else {
                    metadata.set(ISSUBJECTAUTHORITY, Boolean.TRUE.toString());
                }
                metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                metadata.set(TikaCoreProperties.TITLE, "Certificado:" + cert.getSubjectX500Principal().getName());
            }


        } catch (Exception e) {
            throw new TikaException("Invalid or unkown certificate format.", e);
        } finally {
            tis.close();
        }
    }

    static private String CSS = "th {text-align:left; font-family: Arial, sans-serif; background-color: rgb(240, 240, 240);} ";

    private void generateCertificateHtml(X509Certificate cert, XHTMLContentHandler xhtml)
            throws UnsupportedEncodingException, SAXException {

        xhtml.startElement("style");
        xhtml.characters(CSS);
        xhtml.endElement("style");

        xhtml.startElement("table border='1'");
        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.SubjectX500"));// "Subject X500"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getSubjectX500Principal().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.Subject"));// "Subject"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getSubjectDN().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.Version"));// "Version"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(Integer.toString(cert.getVersion()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.SerialNumber"));// "Serial Number"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getSerialNumber().toString());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.SignatureAlgorithm"));// "Signature Algorithm"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getSigAlgName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.IssuerX500"));// "Issuer X500"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getIssuerX500Principal().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.Issuer"));// "Issuer"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(cert.getIssuerX500Principal().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        DateFormat df = DateFormat.getDateTimeInstance();
        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.ValidFrom"));// "Valid from"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(df.format(cert.getNotBefore()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.ValidTo"));// "Valid to"
        xhtml.endElement("th");
        xhtml.startElement("td");
        xhtml.characters(df.format(cert.getNotAfter()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.AlternativeNames"));// "Alternative Names:"
        xhtml.endElement("th");
        xhtml.startElement("td");
        List<String> altNamesStrs = getAltNames(cert);
        for (String altNameStr : altNamesStrs) {
            xhtml.characters(altNameStr);
            xhtml.startElement("br");
            xhtml.endElement("br");// linebreak
        }
        xhtml.endElement("td");
        xhtml.endElement("tr");
        xhtml.endElement("table");

        xhtml.startElement("table");
        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters(Messages.getString("CertificateParser.Details"));
        xhtml.endElement("th");
        xhtml.endElement("tr");
        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.startElement("pre");
        xhtml.characters(cert.toString());
        xhtml.endElement("pre");
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.endElement("table");

    }

    private List<String> getAltNames(X509Certificate cert) {
        List<String> altNamesStrs = new ArrayList<String>();
        try {
            Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            if(altNames != null) {
            for (List<?> sanItem : altNames) {
                final Integer itemType = (Integer) sanItem.get(0);
                if (itemType == 0) {
                    String altNameStr = null;
                    final byte[] altNameBytes = (byte[]) sanItem.get(1);
                    ASN1Sequence altNameSeq = getAltnameSequence(altNameBytes);
                    final ASN1TaggedObject obj = (ASN1TaggedObject) altNameSeq.getObjectAt(1);
                    if (obj != null) {
                        ASN1Primitive prim = obj.getLoadedObject();
                        // can be tagged one more time
                        if (prim instanceof ASN1TaggedObject) {
                            prim = ASN1TaggedObject.getInstance(((ASN1TaggedObject) prim)).getLoadedObject();
                        }

                        if (prim instanceof ASN1OctetString) {
                            altNameStr = new String(((ASN1OctetString) prim).getOctets());
                        } else if (prim instanceof ASN1String) {
                            altNameStr = ((ASN1String) prim).getString();
                        }
                    }
                    if (altNameStr != null) {
                        altNamesStrs.add(altNameStr);
                    }
                }
                if (itemType == 1) {
                    altNamesStrs.add(sanItem.get(1).toString());
                }
              }
            }
            if (altNamesStrs.size() == 0) {
                altNamesStrs.add(NOALTNAMES);
            }
        } catch (IOException | CertificateParsingException e) {
            // ignore error.
        }
        return altNamesStrs;
    }

    private ASN1Sequence getAltnameSequence(final byte[] sanValue) throws IOException {
        ASN1Primitive obj = null;
        try (final ByteArrayInputStream baInput = new ByteArrayInputStream(sanValue)) {
            try (final ASN1InputStream asnInput = new ASN1InputStream(baInput)) {
                obj = asnInput.readObject();
            }
            if (obj != null) {
                return ASN1Sequence.getInstance(obj);
            } else {
                return null;
            }
        }
    }

    private Date toDate(String timestamp) {
        return DatatypeConverter.parseDateTime(timestamp).getTime();
    }
}
