package com.zensyra.collector.suunto.mapping;

import java.util.Map;

/**
 * Official Suunto {@code activityId} → sport-name table, taken verbatim from
 * Suunto's published Activities document (linked from the apizone FAQ):
 * https://aspartnercontent.blob.core.windows.net/apizone/docs/Activities.pdf
 *
 * <p>Covers ids 0–121 as published (id 89 does not exist in Suunto's own
 * table; ids 4–9 are all the generic "Sports"). Unknown ids fall back to the
 * raw numeric value as a string rather than guessing a name, so a future
 * Suunto sport never blocks ingestion and stays greppable for backfill.
 */
public final class SuuntoSportType {

    private static final Map<Integer, String> NAMES = Map.ofEntries(
            Map.entry(0, "Walking"),
            Map.entry(1, "Running"),
            Map.entry(2, "Cycling"),
            Map.entry(3, "Cross-country skiing"),
            Map.entry(4, "Sports"),
            Map.entry(5, "Sports"),
            Map.entry(6, "Sports"),
            Map.entry(7, "Sports"),
            Map.entry(8, "Sports"),
            Map.entry(9, "Sports"),
            Map.entry(10, "Mountain biking"),
            Map.entry(11, "Hiking"),
            Map.entry(12, "Roller skating"),
            Map.entry(13, "Downhill skiing"),
            Map.entry(14, "Paddling"),
            Map.entry(15, "Rowing"),
            Map.entry(16, "Golfing"),
            Map.entry(17, "Indoor sports"),
            Map.entry(18, "Parkouring"),
            Map.entry(19, "Ball games"),
            Map.entry(20, "Outdoor gym"),
            Map.entry(21, "Swimming"),
            Map.entry(22, "Trail running"),
            Map.entry(23, "Gym"),
            Map.entry(24, "Nordic walking"),
            Map.entry(25, "Horseback riding"),
            Map.entry(26, "Motorsports"),
            Map.entry(27, "Skateboarding"),
            Map.entry(28, "Water sports"),
            Map.entry(29, "Climbing"),
            Map.entry(30, "Snowboarding"),
            Map.entry(31, "Ski touring"),
            Map.entry(32, "Fitness class"),
            Map.entry(33, "Soccer"),
            Map.entry(34, "Tennis"),
            Map.entry(35, "Basketball"),
            Map.entry(36, "Badminton"),
            Map.entry(37, "Baseball"),
            Map.entry(38, "Volleyball"),
            Map.entry(39, "American football"),
            Map.entry(40, "Table tennis"),
            Map.entry(41, "Racquet ball"),
            Map.entry(42, "Squash"),
            Map.entry(43, "Floorball"),
            Map.entry(44, "Handball"),
            Map.entry(45, "Softball"),
            Map.entry(46, "Bowling"),
            Map.entry(47, "Cricket"),
            Map.entry(48, "Rugby"),
            Map.entry(49, "Ice skating"),
            Map.entry(50, "Ice hockey"),
            Map.entry(51, "Yoga/pilates"),
            Map.entry(52, "Indoor cycling"),
            Map.entry(53, "Treadmill"),
            Map.entry(54, "Crossfit"),
            Map.entry(55, "Crosstrainer"),
            Map.entry(56, "Roller skiing"),
            Map.entry(57, "Indoor rowing"),
            Map.entry(58, "Stretching"),
            Map.entry(59, "Track and field"),
            Map.entry(60, "Orienteering"),
            Map.entry(61, "Standup paddling"),
            Map.entry(62, "Combat sport"),
            Map.entry(63, "Kettlebell"),
            Map.entry(64, "Dancing"),
            Map.entry(65, "Snow shoeing"),
            Map.entry(66, "Frisbee golf"),
            Map.entry(67, "Futsal"),
            Map.entry(68, "Multisport"),
            Map.entry(69, "Aerobics"),
            Map.entry(70, "Trekking"),
            Map.entry(71, "Sailing"),
            Map.entry(72, "Kayaking"),
            Map.entry(73, "Circuit training"),
            Map.entry(74, "Triathlon"),
            Map.entry(75, "Padel"),
            Map.entry(76, "Cheerleading"),
            Map.entry(77, "Boxing"),
            Map.entry(78, "Scubadiving"),
            Map.entry(79, "Freediving"),
            Map.entry(80, "Adventure racing"),
            Map.entry(81, "Gymnastics"),
            Map.entry(82, "Canoeing"),
            Map.entry(83, "Mountaineering"),
            Map.entry(84, "Telemarkskiing"),
            Map.entry(85, "Openwater swimming"),
            Map.entry(86, "Windsurfing"),
            Map.entry(87, "Kitesurfing"),
            Map.entry(88, "Paragliding"),
            Map.entry(90, "Snorkeling"),
            Map.entry(91, "Surfing"),
            Map.entry(92, "Swimrun"),
            Map.entry(93, "Duathlon"),
            Map.entry(94, "Aquathlon"),
            Map.entry(95, "Obstacle racing"),
            Map.entry(96, "Fishing"),
            Map.entry(97, "Hunting"),
            Map.entry(98, "Transition"),
            Map.entry(99, "Gravel cycling"),
            Map.entry(100, "Mermaiding"),
            Map.entry(101, "Spearfishing"),
            Map.entry(102, "Jump rope"),
            Map.entry(103, "Track running"),
            Map.entry(104, "Calisthenics"),
            Map.entry(105, "E-biking"),
            Map.entry(106, "E-mtb"),
            Map.entry(107, "Backcountry skiing"),
            Map.entry(108, "Wheelchair sport"),
            Map.entry(109, "Hand cycling"),
            Map.entry(110, "Splitboarding"),
            Map.entry(111, "Biathlon"),
            Map.entry(112, "Meditation"),
            Map.entry(113, "Field hockey"),
            Map.entry(114, "Cyclocross"),
            Map.entry(115, "Vertical running"),
            Map.entry(116, "Ski mountaineering"),
            Map.entry(117, "Skate skiing"),
            Map.entry(118, "Classic skiing"),
            Map.entry(119, "Chores"),
            Map.entry(120, "Pilates"),
            Map.entry(121, "Yoga"));

    private SuuntoSportType() {
    }

    /**
     * @return the official sport name for a Suunto activityId, the raw id as a
     *         string when the id is not in the published table, or null when
     *         the workout carried no activityId at all
     */
    public static String nameFor(Integer activityId) {
        if (activityId == null) {
            return null;
        }
        return NAMES.getOrDefault(activityId, String.valueOf(activityId));
    }
}
