package snyk.analytics;

import java.util.List;

public final class ItlyOverrideHelper {
  public static String[] convertProducts(List<AnalysisIsReady.AnalysisType> products) {
    if (products == null || products.isEmpty()) {
      return new String[0];
    }

    return products.stream()
                   .map(AnalysisIsReady.AnalysisType::getAnalysisType)
                   .toArray(String[]::new);
  }
}
