package ca.spottedleaf.converter.util;

import ca.spottedleaf.converter.types.ListType;
import ca.spottedleaf.converter.types.MapType;
import ca.spottedleaf.converter.types.TypeUtil;
import java.util.function.Function;

public final class RenameHelper {

    // assumes no two or more entries are renamed to a single value, otherwise result will be only one of them will win
    // and there is no defined winner in such a case
    public static boolean renameKeys(final MapType data, final Function<String, String> renamer) {
        if (data == null) {
            return false;
        }

        return data.renameKeys(renamer);
    }

    // Clobbers anything in toKey if fromKey exists
    public static boolean renameSingle(final MapType data, final String fromKey, final String toKey) {
        if (data == null) {
            return false;
        }

        return data.rename(fromKey, toKey);
    }

    public static void renameString(final MapType data, final String key, final Function<String, String> renamer) {
        if (data == null) {
            return;
        }

        final String value = data.getString(key);
        if (value == null) {
            return;
        }

        final String renamed = renamer.apply(value);
        if (renamed == null) {
            return;
        }

        data.setString(key, renamed);
    }

    public static void renameListMapItems(final MapType data, final String listPath, final String mapPath,
                                          final Function<String, String> renamer) {
        if (data == null) {
            return;
        }

        final ListType list = data.getListUnchecked(listPath);
        if (list == null) {
            return;
        }

        for (int i = 0, len = list.size(); i < len; ++i) {
            RenameHelper.renameString(list.getMap(i, null), mapPath, renamer);
        }
    }

    // sets value at dstPath in dst to a copied value of the value at srcPath in src
    public static boolean copy(final MapType src, final String srcPath, final MapType dst, final String dstPath) {
        final Object val = src.getGeneric(srcPath);
        if (val == null) {
            dst.remove(dstPath);
            return false;
        }

        dst.setGeneric(dstPath, TypeUtil.deepCopyGeneric(val));
        return true;
    }

    // moves the value at dstPath in dst to srcPath in src
    public static boolean move(final MapType src, final String srcPath, final MapType dst, final String dstPath) {
        final Object val = src.getGenericAndRemove(srcPath);
        if (val == null) {
            dst.remove(dstPath);
            return false;
        }

        dst.setGeneric(dstPath, val);
        return true;
    }

    private RenameHelper() {}
}
