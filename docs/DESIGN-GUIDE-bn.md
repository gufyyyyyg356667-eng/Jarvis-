# ডিজাইন বদলানোর গাইড (শুধু চেহারা, ফাংশন নয়)

## ১. ওভারলে ইমোজি / অবতার
- অ্যাপে: Settings -> Overlay Avatar থেকে সিলেক্ট করা যায়।
- নতুন ৫টা স্লট: `my1` থেকে `my5`। ফাইল: `app/src/main/assets/overlay-toys/my1.html` ... `my5.html`
- বদলাতে: ফাইলের `<div id="face">` এর ভিতরের ইমোজি বদলান। চাইলে CSS/SVG দিয়ে আরও সাজাতে পারেন।
- একই ইমোজি `overlay-toys/gallery.html` এর `customAvatars` লাইনেও বদলান, যাতে সিলেক্ট করার কার্ডের ছবি মেলে।
- ইন্টারনেট লাগে এমন লাইব্রেরি (esm.sh) ব্যবহার করবেন না। অফলাইনে কাজ করতে হবে।
- আগের ১৭টা অবতারের ফাইলের নাম বদলাবেন না। ভিতরের ডিজাইন বদলানো যাবে।

### ওভারলে orb অবতার (my1 - my4)
- `my1` Nova (নীল), `my2` Violet (বেগুনি), `my3` Saturn (রিং সহ সবুজ-নীল), `my4` Solar (কমলা)। `my5` ইমোজি স্লট।
- চারটাই হোম স্ক্রিনের কণার orb-এর হালকা (2D) সংস্করণ। সব `overlay-toys/orb-core.js` ফাইল ব্যবহার করে।
- রং, গতি, আকার, রিং বদলাতে প্রতিটা ফাইলের নিচের `OrbAvatar.start({...})` অংশ বদলান। রং 0 থেকে 1 স্কেলে [R,G,B]।
- `orb-core.js` মুছবেন না বা নাম বদলাবেন না।

## ২. হোম স্ক্রিন
- orb এর চেহারা: `assets/orb.html`। এখানে `window.JarvisOrb` এ `setState`, `setTheme`, `setBackground`, `setExternalAudio` এই চারটা ফাংশন থাকতেই হবে। এগুলোর নাম আর প্যারামিটার বদলাবেন না।
- হোমের অন্য অংশ: `res/layout/fragment_home.xml`। রং, সাইজ, মার্জিন, ছবি বদলাতে পারেন, কিন্তু কোনো `android:id` মুছবেন না বা নাম বদলাবেন না।
- HUD পেজ: `assets/hud/index.html`। `window.setOrbState`, `setAudioLevel`, `setLiveTime`, `setMicMuted`, `setPowerState`, `syncChatTurns` ইত্যাদি ফাংশন আর `JarvisBridge` এর বোতামের কল (`onOrbClicked`, `onMicClicked`, `onPowerClicked`, `onSettingsClicked`, `onHistoryClicked`, `onScreenShareClicked`) রাখতে হবে।

## ৩. রং ও থিম
- `res/values/` এর রঙের ফাইল আর `res/drawable/` এর ব্যাকগ্রাউন্ড ফাইল।

## নিরাপদ নিয়ম
1. প্যাকেজ নাম `com.jarvis.assistant` বদলাবেন না।
2. `.kt` ফাইল ছোঁবেন না।
3. একবারে একটা জিনিস বদলান, তারপর পুশ করে GitHub Actions-এ বিল্ড দেখুন।
4. কিছু ভাঙলে শেষ কমিটে ফিরুন: `git revert HEAD` তারপর `git push`।
