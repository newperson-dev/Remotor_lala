package com.cheer_team.remotor_lala.config;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

/**
 * 把 config.yaml 的 layout.views 应用到实际控件上。
 *
 * <p>父容器是 ConstraintLayout 时用 {@link ConstraintSet}（支持百分比与锚点重定位），
 * 其它容器（LinearLayout / FrameLayout）直接改 LayoutParams（支持 weight）。
 */
public final class LayoutApplier {

    /** 按配置里的 id 名找到控件（XML 里的、或运行时动态创建的都可以）。 */
    public interface ViewResolver {
        View findView(String idName);
    }

    private static final int[] ALL_SIDES = {
            ConstraintSet.START, ConstraintSet.END,
            ConstraintSet.LEFT, ConstraintSet.RIGHT,
            ConstraintSet.TOP, ConstraintSet.BOTTOM,
            ConstraintSet.BASELINE
    };

    private final LayoutConfig config;
    private final float density;
    private final ViewResolver resolver;

    public LayoutApplier(LayoutConfig config, float density, ViewResolver resolver) {
        this.config = config;
        this.density = density;
        this.resolver = resolver;
    }

    public float density() {
        return density;
    }

    /** 应用全部布局规格，返回成功作用的控件数量。 */
    public int applyAll() {
        if (!config.apply) {
            return 0;
        }
        int count = 0;
        for (ViewLayout spec : config.views.values()) {
            View view = resolver.findView(spec.id);
            if (view == null) {
                continue;
            }
            if (apply(view, spec)) {
                count++;
            }
        }
        return count;
    }

    /** 只应用某个 id 的规格（动态创建控件时用）。 */
    public boolean applyTo(View view, String idName) {
        if (!config.apply || view == null) {
            return false;
        }
        ViewLayout spec = config.forId(idName);
        return spec != null && apply(view, spec);
    }

    public boolean apply(View view, ViewLayout spec) {
        if (view == null || spec == null) {
            return false;
        }
        ViewGroup parent = view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        if (parent == null) {
            return false;
        }
        if (parent instanceof ConstraintLayout) {
            return applyInConstraintLayout((ConstraintLayout) parent, view, spec);
        }
        return applyInPlainContainer(parent, view, spec);
    }

    // ------------------------------------------------------------ 约束布局

    private boolean applyInConstraintLayout(ConstraintLayout parent, View view, ViewLayout spec) {
        int viewId = view.getId();
        if (viewId == View.NO_ID) {
            return false;
        }
        ConstraintSet set = new ConstraintSet();
        set.clone(parent);

        if (spec.width != null) {
            applySize(set, viewId, spec.width, true);
        }
        if (spec.height != null) {
            applySize(set, viewId, spec.height, false);
        }
        if (spec.hasMarginStart()) {
            set.setMargin(viewId, ConstraintSet.START, spec.marginStartPx(density, config.scale));
        }
        if (spec.hasMarginEnd()) {
            set.setMargin(viewId, ConstraintSet.END, spec.marginEndPx(density, config.scale));
        }
        if (spec.hasMarginTop()) {
            set.setMargin(viewId, ConstraintSet.TOP, spec.marginTopPx(density, config.scale));
        }
        if (spec.hasMarginBottom()) {
            set.setMargin(viewId, ConstraintSet.BOTTOM, spec.marginBottomPx(density, config.scale));
        }
        if (spec.anchor != null) {
            applyAnchorToSet(set, viewId, spec.anchor);
        }
        set.applyTo(parent);
        return true;
    }

    private void applySize(ConstraintSet set, int viewId, SizeSpec size, boolean horizontal) {
        switch (size.unit) {
            case PERCENT:
                // 百分比尺寸在约束布局里必须配合 0dp(match constraint)，否则不生效
                if (horizontal) {
                    set.constrainPercentWidth(viewId, size.value);
                    set.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT);
                } else {
                    set.constrainPercentHeight(viewId, size.value);
                    set.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT);
                }
                break;
            case MATCH_PARENT:
                if (horizontal) {
                    set.constrainWidth(viewId, ViewGroup.LayoutParams.MATCH_PARENT);
                } else {
                    set.constrainHeight(viewId, ViewGroup.LayoutParams.MATCH_PARENT);
                }
                break;
            case WRAP_CONTENT:
                if (horizontal) {
                    set.constrainWidth(viewId, ConstraintSet.WRAP_CONTENT);
                } else {
                    set.constrainHeight(viewId, ConstraintSet.WRAP_CONTENT);
                }
                break;
            default:
                int px = size.resolvePx(density, config.scale, 0);
                if (horizontal) {
                    set.constrainWidth(viewId, px);
                } else {
                    set.constrainHeight(viewId, px);
                }
                break;
        }
    }

    /**
     * 只重新吸附位置（用于动态创建到 ConstraintLayout 里、但配置没写 anchor 时的兜底，
     * 否则控件会因为没有约束堆在左上角）。
     */
    public boolean applyAnchor(View view, String anchor) {
        if (view == null || !(view.getParent() instanceof ConstraintLayout)) {
            return false;
        }
        ConstraintLayout parent = (ConstraintLayout) view.getParent();
        int viewId = view.getId();
        if (viewId == View.NO_ID) {
            return false;
        }
        ConstraintSet set = new ConstraintSet();
        set.clone(parent);
        applyAnchorToSet(set, viewId, anchor);
        set.applyTo(parent);
        return true;
    }

    /** 把控件重新吸附到父容器的指定位置；anchor 形如 bottom_start / top_center / center。 */
    private void applyAnchorToSet(ConstraintSet set, int viewId, String anchor) {
        for (int side : ALL_SIDES) {
            set.clear(viewId, side);
        }
        String value = anchor.trim().toLowerCase();
        boolean top = value.startsWith("top");
        boolean bottom = value.startsWith("bottom");
        boolean start = value.endsWith("start");
        boolean end = value.endsWith("end");

        if (top) {
            set.connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        } else if (bottom) {
            set.connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        } else {
            set.connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        }

        if (start) {
            set.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        } else if (end) {
            set.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        } else {
            set.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        }
    }

    // ------------------------------------------------------------ 普通容器

    private boolean applyInPlainContainer(ViewGroup parent, View view, ViewLayout spec) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return false;
        }
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        boolean applied = true;

        if (spec.width != null) {
            if (spec.width.needsParentSize(parent.getWidth())) {
                applied = false; // 百分比要等父容器测量完成，稍后再应用一次
            } else {
                marginParams.width = spec.width.resolvePx(density, config.scale, parent.getWidth());
            }
        }
        if (spec.height != null) {
            if (spec.height.needsParentSize(parent.getHeight())) {
                applied = false;
            } else {
                marginParams.height = spec.height.resolvePx(density, config.scale, parent.getHeight());
            }
        }
        if (spec.hasMarginStart()) {
            marginParams.setMarginStart(spec.marginStartPx(density, config.scale));
        }
        if (spec.hasMarginEnd()) {
            marginParams.setMarginEnd(spec.marginEndPx(density, config.scale));
        }
        if (spec.hasMarginTop()) {
            marginParams.topMargin = spec.marginTopPx(density, config.scale);
        }
        if (spec.hasMarginBottom()) {
            marginParams.bottomMargin = spec.marginBottomPx(density, config.scale);
        }
        if (spec.weight != null && marginParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) marginParams).weight = spec.weight;
        }
        view.setLayoutParams(marginParams);
        return applied;
    }
}
