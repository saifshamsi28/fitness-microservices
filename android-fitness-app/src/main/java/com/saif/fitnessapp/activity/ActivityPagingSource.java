package com.saif.fitnessapp.activity;

import androidx.annotation.NonNull;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import com.saif.fitnessapp.network.ApiService;
import com.saif.fitnessapp.network.dto.ActivityResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Response;

public class ActivityPagingSource extends RxPagingSource<Integer, ActivityResponse> {
    private static final int STARTING_PAGE_INDEX = 0;
    private static final int PAGE_SIZE = 10;

    private final ApiService apiService;
    private final String userId;

    public ActivityPagingSource(ApiService apiService, String userId) {
        this.apiService = apiService;
        this.userId = userId;
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, ActivityResponse>> loadSingle(LoadParams<Integer> params) {
        int pageIndex = params.key != null ? params.key : STARTING_PAGE_INDEX;

        return Single.fromCallable(() -> {
            Response<List<ActivityResponse>> response = apiService.getActivities(
                    pageIndex,
                    PAGE_SIZE,
                    userId
            ).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<ActivityResponse> data = response.body();
                Integer nextKey = data.isEmpty() ? null : pageIndex + 1;
                Integer prevKey = pageIndex > 0 ? pageIndex - 1 : null;

                return new LoadResult.Page<>(
                        data,
                        prevKey,
                        nextKey
                );
            } else {
                return new LoadResult.Error<>(new Exception("Failed to load activities"));
            }
        }).onErrorResumeNext(t -> Single.just(new LoadResult.Error<>(t)));
    }

    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, ActivityResponse> state) {
        return state.getAnchorPosition() != null ? state.getAnchorPosition() : null;
    }
}
