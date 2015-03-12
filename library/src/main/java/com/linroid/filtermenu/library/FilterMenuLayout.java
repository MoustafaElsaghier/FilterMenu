package com.linroid.filtermenu.library;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

/**
 * Created by linroid on 15/3/4.
 */
public class FilterMenuLayout extends ViewGroup{
    public static final String TAG = "FilterMenuLayout";

    public static final int STATE_COLLAPSE = 0x1;
    public static final int STATE_EXPAND = 0x2;

    public static final int DURATION = 400;
    private static final int DURATION_BETWEEN_ITEM = 50;

    //arc radius when menu is collapsed
    private int collapsedRadius;
    //arc radius when menu is expanded
    private int expandedRadius;

    int primaryColor;
    int primaryDarkColor;

    private Point center;
    private int state = STATE_COLLAPSE;
    private Paint primaryPaint;
    private Paint primaryDarkPaint;

    private OvalOutline outlineProvider;
    private Rect menuBounds;
    private int centerLeft, centerRight, centerTop, centerBottom;

    private List<Point> intersectPoints = new ArrayList<>();

    private float expandProgress = 0;
    private FilterMenuDrawable drawable;

    ObjectAnimator expandAnimator;
    ValueAnimator colorAnimator;

    /**
     * TODO：自动检测角度
     */
    double fromAngle;
    double toAngle;
    private FilterMenu menu;

    public FilterMenuLayout(Context context) {
        super(context);
        init(context, null);
    }

    public FilterMenuLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FilterMenuLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @DebugLog
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FilterMenuLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    @DebugLog
    private void init(Context ctx, AttributeSet attrs) {
        Log.i(TAG, "init");
        float density = getResources().getDisplayMetrics().density;
        TypedArray ta = ctx.getResources().obtainAttributes(attrs, R.styleable.FilterMenuLayout);
        int defaultCollapsedRadius = (int) (65 / 2.f * density + 0.5);
        int defaultExpandedRadius = (int) (65 * 2 * density + 0.5);
        collapsedRadius = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_collapsedRadius, defaultCollapsedRadius);
        expandedRadius = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_expandedRadius, defaultExpandedRadius);


        centerLeft = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_centerLeft, 0);
        centerRight = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_centerRight, 0);
        centerTop = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_centerTop, 0);
        centerBottom = ta.getDimensionPixelSize(R.styleable.FilterMenuLayout_centerBottom, 0);

        primaryColor = ta.getColor(R.styleable.FilterMenuLayout_primaryColor, getResources().getColor(android.R.color.holo_blue_bright));
        primaryDarkColor = ta.getColor(R.styleable.FilterMenuLayout_primaryDarkColor, getResources().getColor(android.R.color.holo_blue_dark));
        ta.recycle();

        centerLeft = centerLeft!=0 && centerLeft<collapsedRadius ? collapsedRadius : centerLeft;
        centerTop = centerTop!=0 && centerTop<collapsedRadius ? collapsedRadius : centerTop;
        centerRight = centerRight!=0 && centerRight<collapsedRadius ? collapsedRadius : centerRight;
        centerBottom = centerBottom!=0 && centerBottom<collapsedRadius ? collapsedRadius : centerBottom;
        if (centerLeft == 0 && centerRight == 0) {
            centerLeft = collapsedRadius;
        }
        if (centerTop == 0 && centerBottom == 0) {
            centerTop = collapsedRadius;
        }
        center = new Point();
        center.set(collapsedRadius, expandedRadius);


        if (collapsedRadius > expandedRadius) {
            throw new IllegalArgumentException("expandedRadius must bigger than collapsedRadius");
        }
        primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        primaryPaint.setColor(primaryColor);
        primaryPaint.setStyle(Paint.Style.FILL);

        primaryDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        primaryDarkPaint.setColor(primaryColor);
        primaryDarkPaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new OvalOutline();
        }
        drawable = new FilterMenuDrawable(ctx, Color.WHITE, collapsedRadius);
        menuBounds = new Rect();
        expandAnimator = ObjectAnimator.ofFloat(this, "expandProgress", 0, 0);
        expandAnimator.setInterpolator(new OvershootInterpolator());
        expandAnimator.setDuration(DURATION);

        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), primaryColor, primaryDarkColor);
        colorAnimator.setDuration(DURATION);
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                primaryDarkPaint.setColor((Integer) animation.getAnimatedValue());
            }
        });
        setSoundEffectsEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if(getChildCount()>0){
            throw new IllegalStateException("you should not add  any child view tag ");
        }
    }

    public float getExpandProgress() {
        return expandProgress;
    }

    public void setExpandProgress(float progress) {
        this.expandProgress = progress;
        primaryPaint.setAlpha(Math.min(255, (int) (progress * 255)));

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            invalidateOutline();
        }
        drawable.setExpandProgress(progress);
        invalidate();
    }

    @DebugLog
    void collapse(boolean animate) {
        state = STATE_COLLAPSE;
        for(int i=0; i<getChildCount(); i++){
            getChildAt(i).setVisibility(View.GONE);
        }
        requestLayout();
        if(animate){
            startCollapseAnimation();
        }
    }

    @DebugLog
    void expand(boolean animate) {
        state = STATE_EXPAND;
        for(int i=0; i<getChildCount(); i++){
            getChildAt(i).setVisibility(View.VISIBLE);
        }
        requestLayout();
        if (animate) {
            startExpandAnimation();
        } else {
            setItemsAlpha(1f);
        }
    }
    @DebugLog
    void toggle(boolean animate) {
        if (state== STATE_COLLAPSE) {
            expand(animate);
        } else if (state== STATE_EXPAND) {
            collapse(animate);
        }
    }

    @DebugLog
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);


        setMeasuredDimension(width, height);
        measureChildren(widthMeasureSpec, heightMeasureSpec);

    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who==drawable || super.verifyDrawable(who);
    }

    @DebugLog
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        if (getChildCount() == 0) {
            return;
        }
        calculateMenuItemPosition();
        for (int i = 0; i < getChildCount(); i++) {
            FilterMenu.Item item = (FilterMenu.Item) getChildAt(i).getTag();
            item.setBounds(
                    l + item.getX(),
                    t + item.getY(),
                    l + item.getX() + item.getView().getMeasuredWidth(),
                    t + item.getY() + item.getView().getMeasuredHeight()
            );
            Rect bounds = item.getBounds();
            item.getView().layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
        }

    }
    Point touchPoint = new Point();
    boolean inChild = false;
    FilterMenu.Item touchedItem;
    @DebugLog
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        touchPoint.set((int) event.getX(), (int) event.getY());
        double distance = pointsDistance(touchPoint, center);
        if(distance > (collapsedRadius+(expandedRadius-collapsedRadius)*expandProgress)){
            if(state == STATE_EXPAND){
                collapse(true);
                return true;
            }
            return false;
        }

        int action = event.getActionMasked();
        switch (action){
            case MotionEvent.ACTION_DOWN: {
                toggle(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE:{
                if(inChild){
                    if(!inArea(touchPoint, touchedItem.getBounds(), .2f)){
                        touchedItem.getView().setPressed(false);
                        inChild = false;
                    }
                }else{
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        FilterMenu.Item item = (FilterMenu.Item) getChildAt(i).getTag();
                        if(inArea(touchPoint, item.getBounds(), .2f)){
                            touchedItem = item;
                            inChild = true;
                            child.dispatchTouchEvent(event);
                            child.setPressed(true);
                            break;
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if(inChild){
                    if(menu!=null){
                        if(menu.getListener()!=null) {
                            collapse(true);
                            menu.getListener().onMenuItemClick(touchedItem.getView(), touchedItem.getPosition());
                        }
                    }

                    touchedItem.getView().setPressed(false);
                    inChild = false;
                }

                break;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        for (int i = 0; i < getChildCount(); i++) {
//            View child = getChildAt(i);
//            FilterMenu.Item item = (FilterMenu.Item) getChildAt(i).getTag();
//            if(inArea(touchPoint, item.getBounds())){
//                return false;
//            }
//        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(outlineProvider);
        }
        int x, y;
        x = centerLeft != 0 ? centerLeft : w - centerRight;
        y = centerTop != 0 ? centerTop : h - centerBottom;
        center.set(x, y);

        int left = Math.max(getPaddingLeft(), center.x - expandedRadius);
        int top = Math.max(getPaddingTop(), center.y - expandedRadius);
        int right = Math.min(w - getPaddingRight(), center.x + expandedRadius);
        int bottom = Math.min(h - getPaddingBottom(), center.y + expandedRadius);

        menuBounds.set(left, top, right, bottom);

        calculateIntersectPoints();
        drawable.setBounds(center.x - drawable.getIntrinsicWidth() / 2,
                center.y - drawable.getIntrinsicHeight() / 2,
                center.x + drawable.getIntrinsicWidth() / 2,
                center.y + drawable.getIntrinsicHeight() / 2
        );

    }

    @DebugLog
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (expandProgress > 0f) {
            canvas.drawCircle(center.x, center.y, collapsedRadius + (expandedRadius - collapsedRadius) * expandProgress, primaryPaint);
        }
        canvas.drawCircle(center.x, center.y, collapsedRadius + (collapsedRadius * .2f * expandProgress), primaryDarkPaint);
        drawable.draw(canvas);
    }


    public void setPrimaryColor(int color) {
        primaryPaint.setColor(color);
        invalidate();
    }

    public void setPrimaryDarkColor(int color) {
        primaryDarkPaint.setColor(color);
        invalidate();
    }

    void startExpandAnimation() {
        //animate circle
        expandAnimator.setFloatValues(getExpandProgress(), 1f);
        expandAnimator.start();

        //animate color
        colorAnimator.setObjectValues(colorAnimator.getAnimatedValue() == null ? primaryColor : colorAnimator.getAnimatedValue(), primaryDarkColor);
        colorAnimator.start();

        //animate menu item
        int delay = 100;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).animate()
                    .setStartDelay(delay)
                    .setDuration(DURATION)
                    .alphaBy(0f)
                    .scaleXBy(0.5f)
                    .scaleX(1f)
                    .scaleYBy(0.5f)
                    .scaleY(1.f)
                    .alpha(1f)
                    .start();
            delay += DURATION_BETWEEN_ITEM;
        }
    }
    void startCollapseAnimation() {
        //animate circle
        expandAnimator.setFloatValues(getExpandProgress(), 0f);
        expandAnimator.start();

        //animate color
        colorAnimator.setObjectValues(colorAnimator.getAnimatedValue()==null ? primaryDarkColor : colorAnimator.getAnimatedValue(), primaryColor);
        colorAnimator.start();

        //animate menu item
        int delay = 100;
        for (int i = getChildCount()-1; i >= 0; i--) {
            getChildAt(i).animate()
                    .setStartDelay(delay)
                    .setDuration(DURATION)
                    .alpha(0)
                    .scaleX(0)
                    .scaleY(0)
                    .start();
            delay += DURATION_BETWEEN_ITEM;
        }

    }

    void setItemsAlpha(float alpha) {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    /**
     * calculate and set position to menu items
     */
    private void calculateMenuItemPosition() {

        float itemRadius = (expandedRadius + collapsedRadius) / 2, f;
        RectF area = new RectF(
                center.x - itemRadius,
                center.y - itemRadius,
                center.x + itemRadius,
                center.y + itemRadius);
        Path path = new Path();
        path.addArc(area, (float) fromAngle, (float) (toAngle - fromAngle));
        PathMeasure measure = new PathMeasure(path, false);
        float len = measure.getLength();
        int divisor = getChildCount();
        float divider = len / divisor;

        for (int i = 0; i < getChildCount(); i++) {
            float[] coords = new float[2];
            measure.getPosTan(i * divider + divider*.5f, coords, null);
            FilterMenu.Item item = (FilterMenu.Item) getChildAt(i).getTag();
            item.setX((int) coords[0] - item.getView().getMeasuredWidth() / 2);
            item.setY((int) coords[1] - item.getView().getMeasuredHeight() / 2);
        }
    }


    /**
     * find all intersect points, and calculate menu items display area;
     */
    private void calculateIntersectPoints() {
        intersectPoints.clear();

        /** order intersect points clockwise **/
        //left edge
        if (center.x - menuBounds.left < expandedRadius) {
            int dy = (int) Math.sqrt(Math.pow(expandedRadius, 2) - Math.pow(center.x - menuBounds.left, 2));
            if (center.y - dy > menuBounds.top) {
                intersectPoints.add(new Point(menuBounds.left, center.y - dy));
            }

            if (center.y + dy < menuBounds.bottom) {
                intersectPoints.add(new Point(menuBounds.left, center.y + dy));
            }

        }
        //top edge
        if (center.y - menuBounds.top < expandedRadius) {
            int dx = (int) Math.sqrt(Math.pow(expandedRadius, 2) - Math.pow(center.y - menuBounds.top, 2));
            if (center.x + dx < menuBounds.right) {
                intersectPoints.add(new Point(center.x + dx, menuBounds.top));
            }
            if (center.x - dx > menuBounds.left) {
                intersectPoints.add(new Point(center.x - dx, menuBounds.top));
            }
        }

        //right edge
        if (menuBounds.right - center.x < expandedRadius) {
            int dy = (int) Math.sqrt(Math.pow(expandedRadius, 2) - Math.pow(menuBounds.right - center.x, 2));

            if (center.y - dy > menuBounds.top) {
                intersectPoints.add(new Point(menuBounds.right, center.y - dy));
            }
            if (center.y + dy < menuBounds.bottom) {
                intersectPoints.add(new Point(menuBounds.right, center.y + dy));
            }

        }
        //bottom edge
        if (menuBounds.bottom - center.y < expandedRadius) {
            int dx = (int) Math.sqrt(Math.pow(expandedRadius, 2) - Math.pow(menuBounds.bottom - center.y, 2));
            if (center.x + dx < menuBounds.right) {
                intersectPoints.add(new Point(center.x + dx, menuBounds.bottom));
            }
            if (center.x - dx > menuBounds.left) {
                intersectPoints.add(new Point(center.x - dx, menuBounds.bottom));
            }
        }


        //find the maximum arc in menuBounds
        int size = intersectPoints.size();
        if (size == 0) {
            fromAngle = 0;
            toAngle = 360;
            return;
        }
        int indexA = size - 1;
//        double maxDistance = pointsDistance(intersectPoints.get(0), intersectPoints.get(indexA));
        double maxAngle = arcAngle(center, intersectPoints.get(0), intersectPoints.get(indexA), menuBounds, expandedRadius);
        for (int i = 0; i < size - 1; i++) {
            Point a = intersectPoints.get(i);
            Point b = intersectPoints.get(i+1);
//            double distance = pointsDistance(intersectPoints.get(i), intersectPoints.get(i + 1));
//            if (distance > maxDistance) {
//                indexA = i;
//                maxDistance = distance;
//            }
            double angle = arcAngle(center, a, b, menuBounds, expandedRadius);
            if (angle > maxAngle) {
                indexA = i;
                maxAngle = angle;
            }
        }

        Point a = intersectPoints.get(indexA);
        Point b = intersectPoints.get(indexA + 1 >= size ? 0 : indexA + 1);
        Point midNormalPoint = findMidNormalPoint(center, a, b, menuBounds, expandedRadius);

        //make sure a->midNormalPoint is ordered clockwise
        double cross = (a.x-center.x)*(midNormalPoint.y-center.y)-(midNormalPoint.x-center.x)*(a.y-center.y);
        Point x = new Point(menuBounds.right, center.y);
        if(cross<0){
            Point tmp = a;
            a = b;
            b = tmp;
        }

        fromAngle = pointAngleOnCircle(center, a, x);
        toAngle = pointAngleOnCircle(center, b, x);
        toAngle = toAngle <= fromAngle ? 360+toAngle : toAngle;
    }

    /**
     * calculate arc angle between point a and point b
     * @param center
     * @param a
     * @param b
     * @param area
     * @param radius
     * @return
     */
    private static double arcAngle(Point center, Point a, Point b, Rect area, int radius){
        double angle = threePointsAngle(center, a, b);
        Point innerPoint = findMidNormalPoint(center, a, b, area, radius);
        Point midInsectPoint = new Point((a.x+b.x)/2, (a.y+b.y)/2);
        double distance = pointsDistance(midInsectPoint, innerPoint);
        if(distance>radius){
            return 360 - angle;
        }
        return angle;
    }

    /**
     * find the middle point of two intersect points in circle,only one point will be correct
     * @param center
     * @param a
     * @param b
     * @param area
     * @param radius
     * @return
     */
    private static Point findMidNormalPoint(Point center, Point a, Point b, Rect area, int radius){
        if(a.y==b.y){
            //top
            if(a.y<center.y){
                return new Point((a.x+b.x)/2, center.y+radius);
            }
            //bottom
            return new Point((a.x+b.x)/2, center.y-radius);
        }
        if(a.x==b.x){
            //left
            if(a.x<center.x){
                return new Point(center.x+radius, (a.y+b.y)/2);
            }
            //right
            return new Point(center.x-radius, (a.y+b.y)/2);
        }
        //slope of line ab
        double abSlope =(a.y-b.y) / (a.x-b.x*1.0);
        //slope of midnormal
        double midnormalSlope = -1.0 / abSlope;

        double radian = Math.tan(midnormalSlope);
        int dy = (int) (radius * Math.sin(radian));
        int dx = (int) (radius * Math.cos(radian));
        Point point = new Point(center.x+dx, center.y+dy);
        if(!inArea(point, area, 0)){
            point = new Point(center.x-dx, center.y-dy);
        }
        return point;
    }

    private static double pointAngleOnCircle(Point center,  Point point, Point coor) {
        double angle = threePointsAngle(center, point, coor);
        if(point.y<center.y){
            angle = 360-angle;
        }
        return angle;
    }

    /**
     * judge if an point in the area or not
     * @param point
     * @param area
     * @param offsetRatio
     * @return
     */
    @DebugLog
    public static boolean inArea(Point point, Rect area, float offsetRatio){
        int offset = (int) (area.width()*offsetRatio);
        return point.x>=area.left-offset && point.x<=area.right+offset &&
               point.y>=area.top-offset && point.y<=area.bottom+offset;
    }

    /**
     * calculate the  point a's angle of rectangle consist of point a,point b, point c;
     * @param vertex
     * @param A
     * @param B
     * @return
     */
    private static double threePointsAngle(Point vertex, Point A, Point B) {
        double b = pointsDistance(vertex, A);
        double c = pointsDistance(A, B);
        double a = pointsDistance(B, vertex);

        return Math.toDegrees(Math.acos((a * a + b * b - c * c) / (2 * a * b)));

    }

    /**
     * calculate distance of two points
     * @param a
     * @param b
     * @return
     */
    private static double pointsDistance(Point a, Point b) {
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int getState() {
        return state;
    }

    public void setMenu(FilterMenu menu) {
        this.menu = menu;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public class OvalOutline extends ViewOutlineProvider {
        public OvalOutline() {
            super();
        }

        @Override
        public void getOutline(View view, Outline outline) {
            int radius = (int) (collapsedRadius + (expandedRadius-collapsedRadius)*expandProgress);
            Rect area = new Rect(
                    center.x - radius,
                    center.y - radius,
                    center.x + radius,
                    center.y + radius);
            outline.setRoundRect(area, radius);
        }
    }



}