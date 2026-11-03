package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TemplateBundleCodecTest {
    @Test
    public void templatesSurviveTheRoundTripWithTheirNamesAndValues() throws Exception {
        List<TemplateBundleCodec.Template> source = new ArrayList<>();
        source.add(new TemplateBundleCodec.Template("Дорога", state("visible_CLOCK", "b:true")));
        source.add(new TemplateBundleCodec.Template("Музыка", state("scale_CLOCK", "i:1650")));

        List<TemplateBundleCodec.Template> restored = roundTrip(source);

        assertEquals(2, restored.size());
        assertEquals("Дорога", restored.get(0).name);
        assertEquals(source.get(0).state, restored.get(0).state);
        assertEquals("Музыка", restored.get(1).name);
        assertEquals("i:1650", restored.get(1).state.get("scale_CLOCK"));
    }

    @Test
    public void aTemplateWithManyEntriesRestoresWhole() throws Exception {
        Map<String, String> big = state("visible_CLOCK", "b:true");
        for (int index = 0; index < 600; index++) {
            big.put("free_x_WIDGET_" + index, "i:" + index);
        }
        List<TemplateBundleCodec.Template> restored = roundTrip(
                List.of(new TemplateBundleCodec.Template("Большой", big)));

        assertEquals(big, restored.get(0).state);
    }

    @Test
    public void refusesAFileThatIsNotATemplateFile() {
        byte[] bytes = "<not-a-bundle/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void refusesASettingsBackupHandedToTheTemplateImport() throws Exception {
        String xml = write(List.of(new TemplateBundleCodec.Template(
                "x", state("visible_CLOCK", "b:true")))).replace(
                "Mirror2RearUltra templates</entry>", "Mirror2RearUltra settings</entry>");
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void refusesAKeyThatCouldReachAnotherSlot() throws Exception {
        Map<String, String> state = state("visible_CLOCK", "b:true");
        state.put("T_1.saved", "b:true");
        byte[] bytes = write(List.of(new TemplateBundleCodec.Template("x", state)))
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void refusesAValueOfAnUnknownKind() throws Exception {
        Map<String, String> state = state("visible_CLOCK", "b:true");
        state.put("scale_CLOCK", "i:notanumber");
        byte[] bytes = write(List.of(new TemplateBundleCodec.Template("x", state)))
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void refusesATemplateWithoutTheMarkThatSaysItIsSaved() throws Exception {
        byte[] bytes = write(List.of(new TemplateBundleCodec.Template(
                "x", mapOf("visible_CLOCK", "b:true")))).getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void refusesAnEmptyOrOverlongName() throws Exception {
        byte[] empty = write(List.of(new TemplateBundleCodec.Template(
                " ", state("visible_CLOCK", "b:true")))).getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(empty)));
        byte[] long_ = write(List.of(new TemplateBundleCodec.Template(
                "n".repeat(TemplateBundleCodec.MAX_NAME_LENGTH + 1),
                state("visible_CLOCK", "b:true")))).getBytes(StandardCharsets.UTF_8);
        assertThrows(TemplateBundleCodec.BundleException.class,
                () -> TemplateBundleCodec.read(new ByteArrayInputStream(long_)));
    }

    private static List<TemplateBundleCodec.Template> roundTrip(
            List<TemplateBundleCodec.Template> templates) throws Exception {
        return TemplateBundleCodec.read(new ByteArrayInputStream(
                write(templates).getBytes(StandardCharsets.UTF_8)));
    }

    private static String write(List<TemplateBundleCodec.Template> templates) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TemplateBundleCodec.write(templates, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    /** A slot's values with the mark every saved template carries. */
    private static Map<String, String> state(String key, String value) {
        Map<String, String> state = new LinkedHashMap<>();
        state.put("saved", "b:true");
        state.put(key, value);
        return state;
    }

    private static Map<String, String> mapOf(String key, String value) {
        Map<String, String> state = new LinkedHashMap<>();
        state.put(key, value);
        return state;
    }
}
