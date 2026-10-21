package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BitmapFontLayoutTest {
    @TempDir Path root;
    private final JsonArray providers = new JsonArray();

    @Test
    void matchesJavaHeightAndAscentIncludingRepresentableNegativeAscent() throws Exception {
        // Client calibration: a native cell starts 4 GUI units above H;
        // Java bitmap top is 7-ascent. These are measured reference bounds.
        int[][] cases = {{4,4,7,11},{4,2,9,13},{4,0,11,15},{2,-2,13,15},{8,8,3,11},{9,8,3,12}};
        for (int i=0;i<cases.length;i++) add(0xE000+i, 16, cases[i][0], cases[i][1], 0,16);
        var output = compile();
        assertTrue(output.result.problems().isEmpty(), output.result.problems().toString());
        BufferedImage page = output.page("E0");
        for (int i=0;i<cases.length;i++) {
            for (int y=0;y<16;y++) assertEquals(y>=cases[i][2] && y<cases[i][3],
                    page.getRGB(i*16,y)>>>24!=0, "case="+i+", y="+y);
        }
    }

    @Test
    void sourceResolutionAndOtherPageMembersCannotMoveOrResizeGlyphs() throws Exception {
        add(0xE000,8,8,7,0,8);
        add(0xE100,64,8,7,0,64);
        int[] before = compile().page("E0").getRGB(0,0,16,16,null,0,16);
        add(0xE001,16,12,10,0,16);
        var output = compile();
        assertArrayEquals(before,output.page("E0").getRGB(0,0,16,16,null,0,16));
        assertArrayEquals(before,output.page("E1").getRGB(0,0,16,16,null,0,16));
    }

    @Test
    void refusesVisibleOverflowWithoutShrinkingOrDrawingInAdjacentCells() throws Exception {
        add(0xE000,16,1024,1024,0,16); // exceeds the largest supported atlas
        add(0xE001,16,9,-1024,0,16);   // baseline outside even the largest cell
        add(0xE002,16,4,4,0,16);
        var output=compile();
        assertEquals(1,output.result.glyphs());
        assertTrue(output.result.problems().stream().anyMatch(p->p.contains("baselines require a Bedrock layout adapter")));
        var page=output.page("E0");
        for(int y=0;y<16;y++) for(int x=0;x<32;x++) assertEquals(0,page.getRGB(x,y));
        assertNotEquals(0,page.getRGB(32,7));
    }

    @Test
    void enlargesCellsForVisibleAscendersAndDescendersWithoutResizingGlyphs() throws Exception {
        add(0xE000,16,16,16,0,16);
        add(0xE001,9,9,0,0,9);
        add(0xE002,9,9,8,0,9);
        var output=compile();
        assertTrue(output.result.problems().isEmpty());
        var page=output.page("E0");
        assertEquals(512,page.getWidth());
        // Native origin is -12 relative to H for a 32px cell.
        assertEquals(0xFFFF0000,page.getRGB(0,3));   // 7-16 = -9 GUI units
        assertEquals(0,page.getRGB(0,19));
        assertEquals(0xFFFF0000,page.getRGB(32,19)); // 7-0 = 7 GUI units
        assertEquals(0,page.getRGB(32,28));
        assertEquals(0xFFFF0000,page.getRGB(64,11)); // 7-8 = -1 GUI unit
        assertEquals(0,page.getRGB(64,20));
    }

    @Test
    void preservesEveryPixelOfWideLabelsAndKeepsNeighborMetricsStable() throws Exception {
        add(0xE001,9,9,8,0,9);
        int[] small=compile().page("E0").getRGB(16,3,9,9,null,0,9);
        BufferedImage label=new BufferedImage(41,9,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<9;y++) for(int x=0;x<41;x++) label.setRGB(x,y,0xFF000000|(x<<8)|y);
        addImage(0xE000,label,9,8);
        var output=compile();
        assertTrue(output.result.problems().isEmpty());
        var page=output.page("E0");
        assertEquals(1024,page.getWidth());
        // A 64px native cell starts 28 GUI units above H; 27 is still H-1.
        assertArrayEquals(label.getRGB(0,0,41,9,null,0,41),page.getRGB(0,27,41,9,null,0,41));
        assertArrayEquals(small,page.getRGB(64,27,9,9,null,0,9));
        assertEquals(0,page.getRGB(41,27));
        assertEquals(0,page.getRGB(0,36));
    }

    @Test
    void retainsLargeMenuBitmapWithoutFittingItToAnEmoji() throws Exception {
        BufferedImage menu=new BufferedImage(176,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++) for(int x=0;x<176;x++) menu.setRGB(x,y,0xFF000000|(x<<8)|y);
        addImage(0xE000,menu,64,48);
        var output=compile();
        assertTrue(output.result.problems().isEmpty());
        var page=output.page("E0");
        assertEquals(4096,page.getWidth());
        assertArrayEquals(menu.getRGB(0,0,176,64,null,0,176),page.getRGB(0,83,176,64,null,0,176));
        assertEquals(0,page.getRGB(176,83));
    }

    @Test
    void transparentPaddingMayLeaveCellWithoutErasingNeighborGlyphs() throws Exception {
        add(0xE000,16,16,11,0,16);
        add(0xE010,16,16,16,8,16); // source padding above cell; visible y=3..10
        var output=compile();
        assertTrue(output.result.problems().isEmpty());
        assertEquals(2,output.result.glyphs());
        var page=output.page("E0");
        for(int y=0;y<16;y++) assertNotEquals(0,page.getRGB(0,y));
        assertEquals(0,page.getRGB(0,18));
        assertNotEquals(0,page.getRGB(0,19));
        assertNotEquals(0,page.getRGB(0,26));
        assertEquals(0,page.getRGB(0,27));
    }

    /**
     * A menu image Java draws mostly below the line needs a 512-pixel cell, which makes its whole page 8192x8192
     * (256 MiB in the client). The build names such pages; with a 256-pixel cap the image moves up to fit instead.
     */
    @Test
    void capsGlyphCellsByMovingImagesDrawnFarFromTheLine() throws Exception {
        add(0xE000,200,200,10,0,200);
        add(0xE001,16,8,7,0,16);
        var full=compile(512);
        assertEquals(8192,full.page("E0").getWidth());
        assertTrue(full.result.notices().stream().anyMatch(n->n.contains("8192x8192")&&n.contains("glyph_E0")),
                full.result.notices().toString());
        var capped=compile(256);
        assertTrue(capped.result.problems().isEmpty(),capped.result.problems().toString());
        assertEquals(2,capped.result.glyphs());
        assertEquals(4096,capped.page("E0").getWidth());
        assertTrue(capped.result.notices().stream().anyMatch(n->n.contains("moved vertically")&&n.contains("ascent 10 -> ")),
                capped.result.notices().toString());
        // The whole image is still drawn, inside its own 256-pixel cell.
        BufferedImage page=capped.page("E0");
        int inked=0;
        for(int y=0;y<256;y++) if(page.getRGB(100,y)>>>24!=0) inked++;
        assertEquals(200,inked);
        assertThrows(IllegalArgumentException.class,()->compile(300));
    }

    /**
     * With the title layout, large glyphs go to pages of their own instead of making the page of the small glyphs
     * next to them 8192x8192; the layout maps the original code points to them.
     */
    @Test
    void groupsLargeGlyphsOnPagesOfTheirOwnWithTheTitleLayout() throws Exception {
        add(0xE000,200,200,10,0,200);
        add(0xE001,16,8,7,0,16);
        var output=compile(512,true);
        assertTrue(output.result.problems().isEmpty(),output.result.problems().toString());
        assertEquals(256,output.page("E0").getWidth(),"the small glyph's page keeps a small cell");
        long large=output.files.entrySet().stream().filter(e->e.getKey().startsWith("font/glyph_")).filter(e->{
            try {return ImageIO.read(new ByteArrayInputStream(e.getValue())).getWidth()==8192;}
            catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        }).count();
        assertEquals(1,large,"the large glyph sits on one page of large cells");
    }

    private void add(int codePoint,int sourceSize,int height,int ascent,int visibleStart,int visibleEnd) throws Exception {
        BufferedImage image=new BufferedImage(sourceSize,sourceSize,BufferedImage.TYPE_INT_ARGB);
        for(int y=visibleStart;y<visibleEnd;y++) for(int x=0;x<sourceSize;x++) image.setRGB(x,y,0xFFFF0000);
        addImage(codePoint,image,height,ascent);
    }

    private void addImage(int codePoint,BufferedImage image,int height,int ascent) throws Exception {
        String name="g"+Integer.toHexString(codePoint);
        Path path=root.resolve("assets/test/textures/"+name+".png");Files.createDirectories(path.getParent());
        ImageIO.write(image,"PNG",path.toFile());
        JsonObject provider=new JsonObject();provider.addProperty("type","bitmap");provider.addProperty("file","test:"+name+".png");
        provider.addProperty("height",height);provider.addProperty("ascent",ascent);
        JsonArray chars=new JsonArray();chars.add(Character.toString(codePoint));provider.add("chars",chars);providers.add(provider);
    }

    private Output compile() throws Exception {
        return compile(512);
    }

    private Output compile(int maxCell) throws Exception {
        return compile(maxCell,false);
    }

    private Output compile(int maxCell,boolean textLayout) throws Exception {
        Path path=root.resolve("assets/minecraft/font/default.json");Files.createDirectories(path.getParent());
        JsonObject font=new JsonObject();font.add("providers",providers);Files.writeString(path,font.toString());
        var config=new TwilightConfig(false,true,false,false,40,100,10_000_000,1000,false,false,List.of(),"auto",false,false,3);
        try(var index=ResourceIndex.build(List.of(new ContentSource("test",ContentSource.Kind.RESOURCE_PACK,root,1)),config)) {
            Map<String,byte[]> files=new LinkedHashMap<>();
            return new Output(new BitmapFontCompiler(index,null,false,textLayout).maxCell(maxCell).compile(files),files);
        }
    }
    private record Output(BitmapFontCompiler.Result result,Map<String,byte[]> files) {
        BufferedImage page(String hex) throws Exception {return ImageIO.read(new ByteArrayInputStream(files.get("font/glyph_"+hex+".png")));}
    }
}
