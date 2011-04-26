/*
 * LensKit, a reference implementation of recommender algorithms.
 * Copyright 2010-2011 Regents of the University of Minnesota
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
/**
 *
 */
package org.grouplens.lenskit.baseline;


import static org.junit.Assert.assertEquals;
import it.unimi.dsi.fastutil.longs.Long2DoubleMaps;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.ArrayList;
import java.util.List;

import org.grouplens.lenskit.data.Rating;
import org.grouplens.lenskit.data.SimpleRating;
import org.grouplens.lenskit.data.context.PackedRatingBuildContext;
import org.grouplens.lenskit.data.context.RatingBuildContext;
import org.grouplens.lenskit.data.dao.RatingCollectionDAO;
import org.grouplens.lenskit.data.dao.RatingDataAccessObject;
import org.grouplens.lenskit.data.vector.MutableSparseVector;
import org.grouplens.lenskit.data.vector.SparseVector;
import org.grouplens.lenskit.util.LongSortedArraySet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test baseline predictors that compute means from data.
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 *
 */
public class TestMeanPredictor {
    private static final double RATINGS_DAT_MEAN = 3.75;
    private RatingDataAccessObject dao;
    private RatingBuildContext ratings;

    @Before
    public void createRatingSource() {
        List<Rating> rs = new ArrayList<Rating>();
        rs.add(new SimpleRating(1, 5, 2));
        rs.add(new SimpleRating(1, 7, 4));
        rs.add(new SimpleRating(8, 4, 5));
        rs.add(new SimpleRating(8, 5, 4));
        dao = new RatingCollectionDAO(rs);
        dao.openSession();
        ratings = PackedRatingBuildContext.make(dao);
    }
    
    @After
    public void closeRatingSession() {
        dao.closeSession();
    }
    
    LongSortedSet itemSet(long item) {
        return new LongSortedArraySet(new long[]{item});
    }

    @Test
    public void testMeanBaseline() {
        BaselinePredictor pred = new GlobalMeanPredictor.Builder().build(ratings);
        SparseVector map = new MutableSparseVector(Long2DoubleMaps.EMPTY_MAP);
        SparseVector pv = pred.predict(10l, map, itemSet(2l));
        assertEquals(RATINGS_DAT_MEAN, pv.get(2l), 0.00001);
    }

    @Test
    public void testUserMeanBaseline() {
        BaselinePredictor pred = new UserMeanPredictor.Builder().build(ratings);
        long[] items = {5, 7, 10};
        double[] ratings = {3, 6, 4};
        SparseVector map = MutableSparseVector.wrap(items, ratings);
        // unseen item
        SparseVector pv = pred.predict(10l, map, itemSet(2l));
        assertEquals(4.33333, pv.get(2l), 0.001);
        // seen item - should be same avg
        pv = pred.predict(10l, map, itemSet(7));
        assertEquals(4.33333, pv.get(7), 0.001);
    }

    @Test
    public void testUserMeanBaselineNoFastutil() {
        // FIXME: is this method still necessary?
        BaselinePredictor pred = new UserMeanPredictor.Builder().build(ratings);
        long[] items = {5, 7, 10};
        double[] ratings = {3, 6, 4};
        SparseVector map = MutableSparseVector.wrap(items, ratings);
        // unseen item
        SparseVector pv = pred.predict(10l, map, itemSet(2));
        assertEquals(4.33333, pv.get(2), 0.001);
        // seen item - should be same avg
        pv = pred.predict(10l, map, itemSet(7));
        assertEquals(4.33333, pv.get(7), 0.001);

        // try twice
        LongCollection items2 = new LongArrayList();
        items2.add(7);
        items2.add(2);
        SparseVector preds = pred.predict(10l, map, items2);
        assertEquals(4.33333, preds.get(2l), 0.001);
        assertEquals(4.33333, preds.get(7l), 0.001);
    }

    /**
     * Test falling back to an empty user.
     */
    @Test
    public void testUserMeanBaselineFallback() {
        BaselinePredictor pred = new UserMeanPredictor.Builder().build(ratings);
        SparseVector map = new MutableSparseVector(Long2DoubleMaps.EMPTY_MAP);
        SparseVector pv = pred.predict(10l, map, itemSet(2));
        assertEquals(RATINGS_DAT_MEAN, pv.get(2), 0.001);
    }

    @Test
    public void testItemMeanBaseline() {
        BaselinePredictor pred = new ItemMeanPredictor.Builder().build(ratings);
        long[] items = {5, 7, 10};
        double[] values = {3, 6, 4};
        SparseVector map = MutableSparseVector.wrap(items, values);
        // unseen item, should be global mean
        SparseVector pv = pred.predict(10l, map, itemSet(2));
        assertEquals(RATINGS_DAT_MEAN, pv.get(2), 0.001);
        // seen item - should be item average
        pv = pred.predict(10l, map, itemSet(5));
        assertEquals(3.0, pv.get(5), 0.001);

        // try twice
        LongCollection items2 = new LongArrayList();
        items2.add(5);
        items2.add(2);
        SparseVector preds = pred.predict(10l, map, items2);
        assertEquals(RATINGS_DAT_MEAN, preds.get(2l), 0.001);
        assertEquals(3.0, preds.get(5l), 0.001);
    }

    @Test
    public void testUserItemMeanBaseline() {
        BaselinePredictor pred = new ItemUserMeanPredictor.Builder().build(ratings);
        long[] items = {5, 7, 10};
        double[] ratings = {3, 6, 4};
        SparseVector map = MutableSparseVector.wrap(items, ratings);
        final double avgOffset = 0.75;

        // unseen item, should be global mean + user offset
        SparseVector pv = pred.predict(10l, map, itemSet(2l));
        assertEquals(RATINGS_DAT_MEAN + avgOffset, pv.get(2), 0.001);
        // seen item - should be item average + user offset
        pv = pred.predict(10l, map, itemSet(5));
        assertEquals(3.0 + avgOffset, pv.get(5), 0.001);

        // try twice
        LongCollection items2 = new LongArrayList();
        items2.add(5);
        items2.add(2);
        SparseVector preds = pred.predict(10l, map, items2);
        assertEquals(RATINGS_DAT_MEAN + avgOffset, preds.get(2l), 0.001);
        assertEquals(3.0 + avgOffset, preds.get(5l), 0.001);
    }

}
