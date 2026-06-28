package com.example.scheduler.service;

import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleUser;
import com.example.scheduler.model.BubbleWageRate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnrichShiftsTest {

    @Test
    public void testEnrichShiftsRegexIdExtraction() throws Exception {
        OrchestrationService orchestrationService = new OrchestrationService(null, null, null);

        // Prepare mock workers list
        BubbleUser user1 = new BubbleUser("1731963242067x219606905011096030", "Kim Smirnov", "Worker", 40, true);
        BubbleUser user2 = new BubbleUser("1776851201376x653447610094539600", "kimoza kimoza", "Worker", 40, true);
        List<BubbleUser> workers = Arrays.asList(user1, user2);

        // Prepare mock shifts with the problematic formats
        BubbleShift shift1 = new BubbleShift(null, "Kim Smirnov (ID: 1731963242067x219606905011096030)", "2026-07-31T05:00:00Z", "2026-07-31T14:00:00Z", "Test notes 1");
        BubbleShift shift2 = new BubbleShift(null, "kimoza kimoza (ID: 1776851201376x653447610094539600)", "2026-07-31T09:00:00Z", "2026-07-31T18:00:00Z", "Test notes 2");
        BubbleShift shift3 = new BubbleShift(null, "1731963242067x219606905011096030", "2026-07-30T09:00:00Z", "2026-07-30T18:00:00Z", "Test notes 3");
        BubbleShift shift4 = new BubbleShift(null, "Kim Smirnov", "2026-07-29T05:00:00Z", "2026-07-29T14:00:00Z", "Test notes 4");

        List<BubbleShift> shifts = new ArrayList<>(Arrays.asList(shift1, shift2, shift3, shift4));
        List<BubbleWageRate> wages = new ArrayList<>();

        System.out.println("=== Running enrichShifts check ===");
        // Invoke the private enrichShifts method using reflection
        ReflectionTestUtils.invokeMethod(
                orchestrationService, 
                "enrichShifts", 
                shifts, 
                workers, 
                wages, 
                "company-123", 
                "store-123"
        );

        // Assert all assignedUsers are correctly resolved to Bubble Unique IDs
        System.out.println("Resolved Shift 1 User: " + shift1.getAssignedUser());
        assertEquals("1731963242067x219606905011096030", shift1.getAssignedUser());

        System.out.println("Resolved Shift 2 User: " + shift2.getAssignedUser());
        assertEquals("1776851201376x653447610094539600", shift2.getAssignedUser());

        System.out.println("Resolved Shift 3 User: " + shift3.getAssignedUser());
        assertEquals("1731963242067x219606905011096030", shift3.getAssignedUser());

        System.out.println("Resolved Shift 4 User: " + shift4.getAssignedUser());
        assertEquals("1731963242067x219606905011096030", shift4.getAssignedUser());

        System.out.println("=== ALL REGEX EXTRACTION ASSERTIONS PASSED ===");
    }
}
