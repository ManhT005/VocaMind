import java.nio.file.*;
import java.text.Normalizer;
public class PlatformNfcProbe {
 static String str(String hex) { StringBuilder s=new StringBuilder(); for(String x:hex.trim().split(" +")) s.appendCodePoint(Integer.parseInt(x,16)); return s.toString(); }
 public static void main(String[] args) throws Exception {
  int rows=0, failures=0;
  for(String line:Files.readAllLines(Path.of(args[0]))) {
   if(line.startsWith("#") || line.startsWith("@") || line.isBlank()) continue;
   String[] f=line.split(";");
   for(int i=0;i<5;i++) {
    String expected=str(f[i<3?1:3]);
    if(!expected.equals(Normalizer.normalize(str(f[i]),Normalizer.Form.NFC))) {
     if(failures<3) System.out.println("NFC mismatch row="+(rows+1)+" column="+(i+1)+" input="+f[i]+" expected="+f[i<3?1:3]);
     failures++;
    }
   }
   rows++;
  }
  System.out.println("rows="+rows+" mismatching transformations="+failures);
 }
}
