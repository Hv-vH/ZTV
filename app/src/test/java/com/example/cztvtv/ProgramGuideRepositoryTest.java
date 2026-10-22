package com.example.cztvtv;

import org.json.JSONException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ProgramGuideRepositoryTest {
    @Test
    public void parsesWebsitesNestedScheduleAndMillisecondStrings() throws Exception {
        String body = "{\"message\":\"success\",\"content\":{\"list\":["
                + "{\"station_id\":6,\"station_name\":\"民生休闲\",\"list\":["
                + "{\"program_title\":\"完整节目名称\",\"play_time\":\"1792598400000\","
                + "\"duration\":\"2100000\"},"
                + "{\"program_title\":\"下一节目\",\"play_time\":1792600500000,"
                + "\"duration\":1200000}]}]}}";
        ProgramGuide guide = ProgramGuideRepository.parseSchedule("20261022", body);
        assertEquals(2, guide.programs.size());
        assertEquals("完整节目名称", guide.programs.get(0).title);
        assertEquals(1792600500000L, guide.endTime(0));
        assertEquals(1, guide.currentIndex(1792600500000L));
    }

    @Test
    public void explicitlyEmptyScheduleIsValid() throws Exception {
        ProgramGuide guide = ProgramGuideRepository.parseSchedule("20261022",
                "{\"content\":{\"list\":[{\"list\":[]}]}}");
        assertTrue(guide.programs.isEmpty());
        assertTrue(ProgramGuideRepository.parseSchedule("20261022",
                "{\"content\":{\"list\":[]}}").programs.isEmpty());
    }

    @Test(expected = JSONException.class)
    public void missingListIsAnErrorRatherThanAnEmptySuccess() throws Exception {
        ProgramGuideRepository.parseSchedule("20261022", "{\"content\":{}}");
    }

    @Test(expected = JSONException.class)
    public void wrongDateResponseIsRejectedInsteadOfCachingYesterday() throws Exception {
        ProgramGuideRepository.parseSchedule("20261023",
                "{\"content\":{\"list\":[{\"list\":[{\"program_title\":\"昨日节目\","
                        + "\"play_time\":\"1792598400000\",\"duration\":\"60000\"}]}]}}");
    }

    @Test(expected = JSONException.class)
    public void invalidProgramsDoNotProduceASuccessfulCache() throws Exception {
        ProgramGuideRepository.parseSchedule("20261022",
                "{\"content\":{\"list\":[{\"list\":[{\"program_title\":\"节目\","
                        + "\"play_time\":\"not a time\"}]}]}}");
    }

    @Test
    public void summaryUsesStationCodeAndSkipsMissingTitles() throws Exception {
        Map<String, String> result = ProgramGuideRepository.parseCurrentPrograms(
                "{\"content\":{\"list\":[{\"station_code\":\"106\","
                        + "\"station_playing\":\" 当前节目 \"},"
                        + "{\"station_code\":\"107\",\"station_playing\":\"\"}]}}");
        assertEquals(1, result.size());
        assertEquals("当前节目", result.get("106"));
    }

    @Test(expected = JSONException.class)
    public void emptySummaryIsReportedAsFailure() throws Exception {
        ProgramGuideRepository.parseCurrentPrograms("{\"content\":{\"list\":[]}}");
    }

    @Test
    public void nullTitlesAreSkippedInsteadOfShowingTheWordNull() throws Exception {
        ProgramGuide guide = ProgramGuideRepository.parseSchedule("20261022",
                "{\"content\":{\"list\":[{\"list\":["
                        + "{\"program_title\":null,\"play_time\":1792598400000},"
                        + "{\"program_title\":\"有效节目\",\"play_time\":1792598400000,"
                        + "\"duration\":60000}]}]}}");
        assertEquals(1, guide.programs.size());
        assertEquals("有效节目", guide.programs.get(0).title);
        Map<String, String> summary = ProgramGuideRepository.parseCurrentPrograms(
                "{\"content\":{\"list\":["
                        + "{\"station_code\":\"106\",\"station_playing\":null},"
                        + "{\"station_code\":\"107\",\"station_playing\":\"有效节目\"}]}}");
        assertFalse(summary.containsKey("106"));
        assertEquals("有效节目", summary.get("107"));
    }
}
