package com.cheer_team.remotor_lala.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.ViewGroup;

import org.junit.Test;

/** SizeSpec：yaml / xml 里各种尺寸写法的解析与换算。 */
public class SizeSpecTest {

    @Test
    public void parsesDpAndBareNumber() {
        SizeSpec dp = SizeSpec.parse("220dp");
        assertNotNull(dp);
        assertEquals(SizeSpec.Unit.DP, dp.unit);
        assertEquals(220f, dp.value, 0.0001f);

        SizeSpec bare = SizeSpec.parse("80");
        assertNotNull(bare);
        assertEquals(SizeSpec.Unit.DP, bare.unit);
        assertEquals(80f, bare.value, 0.0001f);
    }

    @Test
    public void parsesPixelPercentAndKeywords() {
        assertEquals(SizeSpec.Unit.PX, SizeSpec.parse("120px").unit);
        assertEquals(SizeSpec.Unit.PERCENT, SizeSpec.parse("25%").unit);
        assertEquals(0.25f, SizeSpec.parse("25%").value, 0.0001f);
        assertTrue(SizeSpec.parse("match_parent").isMatchParent());
        assertTrue(SizeSpec.parse("wrap_content").isWrapContent());
    }

    @Test
    public void resolvesPixelsWithDensityAndScale() {
        assertEquals(440, SizeSpec.parse("220dp").resolvePx(2f, 1f, 0));
        assertEquals(528, SizeSpec.parse("220dp").resolvePx(2f, 1.2f, 0));
        assertEquals(250, SizeSpec.parse("25%").resolvePx(2f, 1f, 1000));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, SizeSpec.parse("match_parent").resolvePx(2f, 1f, 0));
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, SizeSpec.parse("wrap_content").resolvePx(2f, 1f, 0));
    }

    @Test
    public void percentWaitsForParentSize() {
        SizeSpec percent = SizeSpec.parse("25%");
        assertTrue(percent.needsParentSize(0));
        assertTrue(!percent.needsParentSize(1000));
    }

    @Test
    public void returnsNullOnInvalidInput() {
        assertNull(SizeSpec.parse("220dpp"));
        assertNull(SizeSpec.parse("abc"));
        assertNull(SizeSpec.parse(""));
        assertNull(SizeSpec.parse(null));
    }
}
