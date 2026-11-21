// Gera o wordmark do banner da TV (640x360 px = 320x180 dp em xhdpi).
// Uso: jshell tools/branding/Banner.jsh  (a partir da raiz do repositório)
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

int W = 640, H = 360;
BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
Graphics2D g = img.createGraphics();
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
g.setColor(new Color(0x12, 0x14, 0x17));
g.fillRect(0, 0, W, H);
double cx = 140, cy = 180, r = 64;
g.setColor(new Color(0x38, 0xBD, 0xF8));
g.setStroke(new BasicStroke(13f));
g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
g.fill(new Ellipse2D.Double(cx - 32, cy - 32, 64, 64));
g.setColor(new Color(0xF5, 0xF7, 0xFA));
g.fill(new Ellipse2D.Double(cx + 3, cy - 21, 18, 18));
g.setColor(new Color(0xEF, 0x44, 0x44));
g.fill(new Ellipse2D.Double(cx + 52, cy - 78, 18, 18));
g.setColor(new Color(0xF5, 0xF7, 0xFA));
g.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 64));
g.drawString("Câmeras", 240, 172);
g.setColor(new Color(0x38, 0xBD, 0xF8));
g.setFont(new Font("Segoe UI", Font.PLAIN, 64));
g.drawString("ao Vivo", 240, 238);
g.dispose();
ImageIO.write(img, "png", new File("app/src/main/res/drawable-xhdpi/banner_mark.png"));
System.out.println("banner_mark.png gerado");
/exit
