package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryQuality;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import com.murphypotato.simmctoolset.internal.accessory.parser.ParseResult;
import com.murphypotato.simmctoolset.internal.accessory.parser.ParseState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionReviewConfirmationTest {
    @Test
    void reusesManuallyEditedRecordWhenTheSameStackIsScannedAgain() {
        SessionReviewConfirmation confirmations = new SessionReviewConfirmation();
        AccessorySource source = source("饰品箱", 4);
        ContainerLocation location = ContainerLocation.preset("server", 1);
        AccessoryRecord parsedRecord = record("raw-fingerprint", "解析名称", 2);
        AccessoryRecord editedRecord = record("edited-fingerprint", "人工修正名称", 7);

        ParseResult rescanned = new ParseResult(
            parsedRecord,
            ParseState.NEEDS_REVIEW,
            2,
            2,
            List.of("always-review-enabled"),
            List.of("raw tooltip"),
            List.of()
        );
        confirmations.remember(rescanned, editedRecord, source, location);

        AccessoryRecord reused = confirmations.find(rescanned, source, location).orElseThrow();
        assertEquals(editedRecord.id(), reused.id());
        assertEquals(editedRecord.name(), reused.name());
        assertEquals(editedRecord.level(), reused.level());
        assertEquals(source, reused.source());
    }

    @Test
    void onlyMatchesTheOriginalReliableLocationAndSlot() {
        SessionReviewConfirmation confirmations = new SessionReviewConfirmation();
        AccessorySource original = source("饰品箱", 4);
        ContainerLocation originalLocation = block("server", "minecraft:overworld", 10, 64, 20);
        AccessoryRecord parsedRecord = record("raw-fingerprint", "解析名称", 2);
        AccessoryRecord editedRecord = record("edited-fingerprint", "人工修正名称", 7);
        ParseResult rescanned = parse(parsedRecord);
        confirmations.remember(rescanned, editedRecord, original, originalLocation);
        assertTrue(confirmations.find(rescanned, original, originalLocation).isPresent());
        assertFalse(confirmations.find(rescanned,
            source("饰品箱", 5), originalLocation).isPresent());
        assertFalse(confirmations.find(rescanned,
            source("饰品箱", 4), block("server", "minecraft:overworld", 11, 64, 20)).isPresent());
    }

    @Test
    void unlocatedStacksNeedTheSameContainerTitleInsteadOfOnlyKindAndSlot() {
        SessionReviewConfirmation confirmations = new SessionReviewConfirmation();
        AccessorySource original = source("饰品箱 A", 4);
        ContainerLocation location = ContainerLocation.unlocated("容器界面");
        AccessoryRecord parsedRecord = record("raw-fingerprint", "解析名称", 2);
        ParseResult rescanned = parse(parsedRecord);
        confirmations.remember(rescanned, record("edited-fingerprint", "人工修正名称", 7), original, location);
        assertTrue(confirmations.find(rescanned, original, location).isPresent());
        assertFalse(confirmations.find(rescanned,
            source("饰品箱 B", 4), location).isPresent());
    }

    @Test
    void editsAndDeletesUpdateTheSessionConfirmation() {
        SessionReviewConfirmation confirmations = new SessionReviewConfirmation();
        AccessorySource source = source("饰品箱", 4);
        ContainerLocation location = ContainerLocation.preset("server", 1);
        AccessoryRecord parsedRecord = record("raw-fingerprint", "解析名称", 2);
        AccessoryRecord first = record("accessory-id", "第一次修正", 3);
        AccessoryRecord second = record("accessory-id", "第二次修正", 4);
        confirmations.remember(parse(parsedRecord), first, source, location);

        confirmations.replaceConfirmed(first.id(), second);
        assertEquals("第二次修正", confirmations.find(parse(parsedRecord), source, location).orElseThrow().name());

        confirmations.removeConfirmed(second.id());
        assertTrue(confirmations.find(parse(parsedRecord), source, location).isEmpty());
    }

    @Test
    void reusesSameRawFingerprintEvenWhenTheRescanStillHasParserWarnings() {
        SessionReviewConfirmation confirmations = new SessionReviewConfirmation();
        AccessorySource source = source("饰品箱", 4);
        ContainerLocation location = ContainerLocation.preset("server", 1);
        AccessoryRecord parsedRecord = record("raw-fingerprint", "解析名称", 2);
        AccessoryRecord editedRecord = record("edited-fingerprint", "人工修正名称", 7);
        ParseResult malformedRescan = new ParseResult(
            parsedRecord,
            ParseState.NEEDS_REVIEW,
            2,
            2,
            List.of("invalid-affix-line"),
            List.of("raw tooltip"),
            List.of("无法解析的词条")
        );
        confirmations.remember(malformedRescan, editedRecord, source, location);
        assertEquals(editedRecord.name(),
            confirmations.find(malformedRescan, source, location).orElseThrow().name());

        ParseResult changedRescan = new ParseResult(
            parsedRecord,
            ParseState.NEEDS_REVIEW,
            2,
            2,
            List.of("invalid-affix-line"),
            List.of("changed raw tooltip"),
            List.of("无法解析的词条")
        );
        assertTrue(confirmations.find(changedRescan, source, location).isEmpty());
    }

    private static ParseResult parse(AccessoryRecord record) {
        return new ParseResult(record, ParseState.NEEDS_REVIEW, record.level(), 2, List.of(), List.of(), List.of());
    }

    private static AccessorySource source(String title, int slot) {
        return new AccessorySource("container", title, slot, "槽位 " + slot);
    }

    private static ContainerLocation block(String server, String dimension, int x, int y, int z) {
        return new ContainerLocation("block", server, dimension, x, y, z, "饰品箱", 0, "饰品箱");
    }

    private static AccessoryRecord record(String fingerprint, String name, int level) {
        return new AccessoryRecord(
            "accessory-id-" + fingerprint,
            fingerprint,
            name,
            AccessorySlot.MAIN_RING,
            AccessoryQuality.DIM,
            level,
            List.of(),
            "minecraft:diamond",
            new AccessorySource("container", "饰品箱", 4, "槽位 4")
        );
    }
}
