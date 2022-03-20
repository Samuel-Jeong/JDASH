package dash.mpd.validator;

import dash.mpd.parser.MPDParser;
import dash.mpd.parser.mpd.MPD;
import dash.mpd.validator.rules.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

public class MPDValidator {

    private static final Logger logger = LoggerFactory.getLogger(MPDValidator.class);

    private final Schema schema;
    private final MPDParser mpdParser;

    public MPDValidator(MPDParser mpdParser, String validationXsdPath) throws SAXException, MalformedURLException {
        this.schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new File(validationXsdPath).toURI().toURL());
        this.mpdParser = mpdParser;
        logger.debug("[MPDValidator] ValidationXsdPath=[{}]", validationXsdPath);
    }

    public void validate(MPD mpd) throws IOException, SAXException, ManifestValidationException {
        xsdValidation(mpd);

        List<Violation> violations = dash.mpd.validator.rules.MPDValidator.validate(mpd);
        if (!violations.isEmpty()) {
            throw new ManifestValidationException(String.format("[MPDValidator] Found %d validation errors", violations.size()), violations);
        }
    }

    void xsdValidation(MPD mpd) throws IOException, SAXException {
        byte[] buf = mpdParser.writeAsBytes(mpd);

        int i = 1;
        for (String line : mpdParser.writeAsString(mpd).split("\n")) {
            logger.debug("[MPDValidator] [@VAL@] {}: {}", i++, line);
        }

        Source source = new StreamSource(new ByteArrayInputStream(buf));
        schema.newValidator().validate(source);
    }
}
