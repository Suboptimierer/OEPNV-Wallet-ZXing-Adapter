import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Result;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

/**
 * Ermöglicht der Open-Source-Plattform "ÖPNV-Wallet" den Zugriff auf ZXing-Funktionalitäten via CLI.
 * Es gibt aktuell leider keine native Swift-Library, die Aztec-Codes serverseitig en- und decodieren kann.
 * Diese Klasse dient somit als Adapter zu ZXing und stellt eine Übergangslösung dar.
 * 
 * Aktuell werden ausschließlich Aztec-Codes und ISO-8859-1 unterstützt!
 */
public class Main {

    /** Standard-Höhe für ÖPNV-Wallet Aztec-Codes */
    private static final int AZTEC_CODE_HEIGHT = 500;

    /** Standard-Breite für ÖPNV-Wallet Aztec-Codes */
    private static final int AZTEC_CODE_WIDTH = 500;

    /** Standard-Charset für ÖPNV-Wallet Aztec-Codes */
    private static final String CHARSET = "ISO-8859-1";

    /**
     * Die Main-Methode ist aussschließlich für CLI-Nutzung gedacht.
     * 
     * Beispielnutzung:
     * echo "<base64 message>" | java -jar zxing-adapter.jar aztec-encode => <base64 image>
     * echo "<base64 image>" | java -jar zxing-adapter.jar aztec-decode => <base64 message>
     * Die Nutzdatenübertragung läuft via STDIN + STDOUT und muss immer Base64-enkodiert sein!
     *
     * @param args CLI-Parameter
     */
    public static void main(String[] args) {

        try {
            
            System.setProperty("java.awt.headless", "true");
            
            if(args.length != 1) {
                throw new IllegalArgumentException("API falsch verwendet");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Main.CHARSET));
            String base64Input = reader.lines().collect(Collectors.joining("\n")).trim();
            
            switch(args[0].toLowerCase()) {
                case "aztec-encode":
                    String base64ImageOutput = handleAztecEncodeCommand(base64Input);
                    System.out.println(base64ImageOutput);
                    System.out.flush();
                    break;
                case "aztec-decode":
                    String base64MessageOutput = handleAztecDecodeCommand(base64Input);
                    System.out.println(base64MessageOutput);
                    System.out.flush();
                    break;
                default:
                    throw new IllegalArgumentException("API falsch verwendet");
            }
            
            System.exit(0);

        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
            System.exit(1);
        }

    }

    /**
     * Führt den Encode-Befehl aus.
     * 
     * @param base64Input Die Message (ISO-8859-1) für den Aztec-Code Base64-enkodiert.
     * @return Der Aztec-Code Base64-enkodiert.
     */
    private static String handleAztecEncodeCommand(String base64Input) throws Exception {
        byte[] inputBytes = Base64.getDecoder().decode(base64Input);
        String input = new String(inputBytes, Main.CHARSET);
        BufferedImage newAztecCode = encodeAztecCode(input);
        return imageToBase64(newAztecCode);
    }

    /**
     * Führt den Decode-Befehl aus.
     * 
     * @param base64Input Der Aztec-Code Base64-enkodiert.
     * @return Die Message (ISO-8859-1) für den Aztec-Code Base64-enkodiert.
     */
    private static String handleAztecDecodeCommand(String base64Input) throws Exception {
        BufferedImage aztecCode = base64ToImage(base64Input);
        String result = decodeAztecCode(aztecCode);
        byte[] outputBytes = result.getBytes(Main.CHARSET);
        return Base64.getEncoder().encodeToString(outputBytes);
    }

    /**
     * Dekodiert einen Aztec-Code.
     * 
     * @param image Der Aztec-Code als Bild, der dekodiert werden soll.
     * @return Die im Aztec-Code enthaltene Message (ISO-8859-1).
     */
    private static String decodeAztecCode(BufferedImage image) throws Exception {
        BufferedImage scaledImage = scaleImagePixelated(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(scaledImage)));
        Result result = new AztecReader().decode(bitmap);
        String extractedText = result.getText();
        return extractedText;
    }

    /**
     * Enkodiert einen Aztec-Code.
     * 
     * @param message Die Message (ISO-8859-1), die in den Aztec-Code enkodiert werden soll.
     * @return Der Aztec-Code als Bild.
     */
    private static BufferedImage encodeAztecCode(String message) {
        AztecWriter writer = new AztecWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, Main.CHARSET);
        BitMatrix matrix = writer.encode(message, BarcodeFormat.AZTEC, Main.AZTEC_CODE_WIDTH, Main.AZTEC_CODE_HEIGHT, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    /**
     * Enkodiert ein Bild als Base64-Zeichenkette.
     * 
     * @param image Das Bild, welches als Base64-Zeichenkette enkodiert werden soll. 
     * @return Das Bild enkodiert als Base64-Zeichenkette.
     */
    private static String imageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, "png", baos);
        if(!written) {
            throw new IllegalArgumentException("Bild kann nicht als PNG geschrieben werden");
        }
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Dekodiert eine Base64-Zeichenkette als Bild.
     * 
     * @param base64Image Die Base64-Zeichenkette, welche als Bild dekodiert werden soll.
     * @return Die Base64-Zeichenkette dekodierte als Bild.
     */
    private static BufferedImage base64ToImage(String base64Image) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bais);
        if(image == null) {
            throw new IllegalArgumentException("Ungültiges oder nicht unterstütztes Bildformat");
        }
        return image;
    }

    /**
     * Skaliert ein Bild pixelgenau und ohne Weichzeichner auf eine bestimmte Größe.
     * Es kann passieren, dass ein Aztec-Code zu klein ist, damit ZXing ihn korrekt erkennt.
     * Aus diesem Grund werden alle Bilder, die kleiner als eine bestimmte Grenze sind, größer skaliert.
     * 
     * @param image Das Bild, welches skaliert werden soll.
     * @return Das fertig skalierte Bild.
     */
    private static BufferedImage scaleImagePixelated(BufferedImage image) {
        if (image.getWidth() >= Main.AZTEC_CODE_WIDTH && image.getHeight() >= Main.AZTEC_CODE_HEIGHT) {
            return image;
        }
        BufferedImage scaledImage = new BufferedImage(Main.AZTEC_CODE_WIDTH, Main.AZTEC_CODE_HEIGHT, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawImage(image, 0, 0, Main.AZTEC_CODE_WIDTH, Main.AZTEC_CODE_HEIGHT, null);
        g2d.dispose();
        return scaledImage;
    }

}