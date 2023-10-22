# Add any ProGuard configurations specific to this
# extension here.

-keep public class xyz.kumaraswamy.itoo.Itoo {
    public *;
 }
-keeppackagenames gnu.kawa**, gnu.expr**
-dontwarn
-optimizationpasses 4
-allowaccessmodification
-mergeinterfacesaggressively

-repackageclasses 'xyz/kumaraswamy/itoo/repack'
-flattenpackagehierarchy
-dontpreverify
