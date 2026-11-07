package com.yijia.buttons;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;

final class Ui {
  static final int BACKGROUND = 0xffF6F7FB,
      CARD = 0xffFFFFFF,
      ACCENT = 0xff536CC4,
      MUTED = 0xff647187,
      TEXT = 0xff28364B,
      TINT = 0xffEEF1FC,
      BORDER = 0xffE9EDF4;

  static int dp(Context c, float v) {
    return (int) (c.getResources().getDisplayMetrics().density * v);
  }

  static GradientDrawable background(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static TextView text(Context c, String value, int size, int color) {
    TextView v = new TextView(c);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(c, 3), 1);
    return v;
  }

  static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(1);
    return v;
  }

  static LinearLayout card(LinearLayout parent) {
    Context c = parent.getContext();
    LinearLayout card = column(c);
    card.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
    GradientDrawable shape = background(c, CARD, 20);
    shape.setStroke(dp(c, 1), BORDER);
    card.setBackground(shape);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(c, 16);
    parent.addView(card, p);
    return card;
  }

  static TextView title(LinearLayout parent, String title) {
    TextView v = text(parent.getContext(), title, 18, TEXT);
    v.setTypeface(null, Typeface.BOLD);
    v.setPadding(0, 0, 0, dp(parent.getContext(), 8));
    parent.addView(v);
    return v;
  }

  static TextView note(LinearLayout parent, String value) {
    TextView v = text(parent.getContext(), value, 13, MUTED);
    v.setPadding(0, dp(parent.getContext(), 4), 0, dp(parent.getContext(), 8));
    parent.addView(v);
    return v;
  }

  static Button button(LinearLayout parent, String label, boolean primary, Runnable action) {
    Context c = parent.getContext();
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setStateListAnimator(null);
    b.setElevation(0);
    b.setTextColor(primary ? Color.WHITE : ACCENT);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x225B73CC), background(c, primary ? ACCENT : TINT, 14), null));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(c, 50));
    p.topMargin = dp(c, 10);
    parent.addView(b, p);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static ImageView icon(Context c, String name, int color, int size) {
    ImageView view = new ImageView(c);
    int resource = c.getResources().getIdentifier("ic_" + name, "drawable", c.getPackageName());
    if (resource != 0) view.setImageResource(resource);
    view.setImageTintList(ColorStateList.valueOf(color));
    view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    return view;
  }
}
