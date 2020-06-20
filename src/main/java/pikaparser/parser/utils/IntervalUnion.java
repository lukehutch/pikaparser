//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion
//     and error recovery problems. Luke A. D. Hutchison, May 2020.
//     https://arxiv.org/abs/2005.06444
//
// This software is provided under the MIT license:
//
// Copyright 2020 Luke A. D. Hutchison
//  
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions
// of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//
package pikaparser.parser.utils;

import java.util.NavigableMap;
import java.util.TreeMap;

/** Grammar utils. */
public class IntervalUnion {
    private NavigableMap<Integer, Integer> nonOverlappingRanges = new TreeMap<Integer, Integer>();

    /** Add a range to a union of ranges. */
    public void addRange(int startPos, int endPos) {
        if (endPos < startPos) {
            throw new IllegalArgumentException("endPos < startPos");
        }
        // Try merging new range with floor entry in TreeMap
        var floorEntry = nonOverlappingRanges.floorEntry(startPos);
        var floorEntryStart = floorEntry == null ? null : floorEntry.getKey();
        var floorEntryEnd = floorEntry == null ? null : floorEntry.getValue();
        int newEntryRangeStart;
        int newEntryRangeEnd;
        if (floorEntryStart == null || floorEntryEnd < startPos) {
            // There is no startFloorEntry, or startFloorEntry ends before startPos -- add a new entry
            newEntryRangeStart = startPos;
            newEntryRangeEnd = endPos;
        } else {
            // startFloorEntry overlaps with range -- extend startFloorEntry
            newEntryRangeStart = floorEntryStart;
            newEntryRangeEnd = Math.max(floorEntryEnd, endPos);
        }

        // Try merging new range with the following entry in TreeMap
        var higherEntry = nonOverlappingRanges.higherEntry(newEntryRangeStart);
        var higherEntryStart = higherEntry == null ? null : higherEntry.getKey();
        var higherEntryEnd = higherEntry == null ? null : higherEntry.getValue();
        if (higherEntryStart != null && higherEntryStart <= newEntryRangeEnd) {
            // Expanded-range entry overlaps with the following entry -- collapse them into one
            nonOverlappingRanges.remove(higherEntryStart);
            var expandedRangeEnd = Math.max(newEntryRangeEnd, higherEntryEnd);
            nonOverlappingRanges.put(newEntryRangeStart, expandedRangeEnd);
        } else {
            // There's no overlap, just add the new entry (may overwrite the earlier entry for the range start)
            nonOverlappingRanges.put(newEntryRangeStart, newEntryRangeEnd);
        }
    }

    /** Get the inverse of the intervals in this set within [StartPos, endPos). */
    public IntervalUnion invert(int startPos, int endPos) {
        var invertedIntervalSet = new IntervalUnion();

        int prevEndPos = startPos;
        if (!nonOverlappingRanges.isEmpty()) {
            for (var ent : nonOverlappingRanges.entrySet()) {
                var currStartPos = ent.getKey();
                if (currStartPos > endPos) {
                    break;
                }
                var currEndPos = ent.getValue();
                if (currStartPos > prevEndPos) {
                    // There's a gap of at least one position between adjacent ranges
                    invertedIntervalSet.addRange(prevEndPos, currStartPos);
                }
                prevEndPos = currEndPos;
            }
            var lastEnt = nonOverlappingRanges.lastEntry();
            var lastEntEndPos = lastEnt.getValue();
            if (lastEntEndPos < endPos) {
                // Final range: there is at least one position before endPos
                invertedIntervalSet.addRange(lastEntEndPos, endPos);
            }
        } else {
            invertedIntervalSet.addRange(startPos, endPos);
        }
        return invertedIntervalSet;
    }

    /** Return true if the specified range overlaps with any range in this interval union. */
    public boolean rangeOverlaps(int startPos, int endPos) {
        // Range overlap test: https://stackoverflow.com/a/25369187/3950982
        // (Need to repeat for both floor entry and ceiling entry)
        var floorEntry = nonOverlappingRanges.floorEntry(startPos);
        if (floorEntry != null) {
            var floorEntryStart = floorEntry.getKey();
            var floorEntryEnd = floorEntry.getValue();
            if (Math.max(endPos, floorEntryEnd) - Math.min(startPos, floorEntryStart) < (endPos - startPos)
                    + (floorEntryEnd - floorEntryStart)) {
                return true;
            }
        }
        var ceilEntry = nonOverlappingRanges.ceilingEntry(startPos);
        if (ceilEntry != null) {
            var ceilEntryStart = ceilEntry.getKey();
            var ceilEntryEnd = ceilEntry.getValue();
            if (Math.max(endPos, ceilEntryEnd) - Math.min(startPos, ceilEntryStart) < (endPos - startPos)
                    + (ceilEntryEnd - ceilEntryStart)) {
                return true;
            }
        }
        return false;
    }

    /** Return all the nonoverlapping ranges in this interval union. */
    public NavigableMap<Integer, Integer> getNonOverlappingRanges() {
        return nonOverlappingRanges;
    }
}
